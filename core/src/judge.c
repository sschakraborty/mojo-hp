#define _GNU_SOURCE

#include <regex.h>
#include <pthread.h>
#include <sys/stat.h>
#include <string.h>
#include <sys/syscall.h>
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <ctype.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <sys/resource.h>
#include <stdint.h>
#include <time.h>
#include <sys/time.h>
#include <math.h>
#include <sys/times.h>
#include <dirent.h>

/* 20 ms granularity */

#define MILLIS 1000000
#define DELTA_SLEEP 20*MILLIS
#define GRND_NONBLOCK 0x0001
#define MAX_PATH 256
#define ALPHABET_SIZE 37
#define LOG(...) \
    do { \
        fprintf(stderr, "[+] "); \
        fprintf(stderr, __VA_ARGS__); \
        fflush(stderr); \
    } while (0);
#define ERR(...) \
    do { \
        fprintf(stderr, "[ERR] " __VA_ARGS__); \
        fflush(stderr); \
    } while (0);

/*
 * The program displays logs per line preceded by "[+]"
 * The last line contains -
 *         [+] Status : <status-message-string>
 *
 * the 'status-message-string' is a string from the array
 * named 'status_msgs'.
 */

/*
 * List of Error Codes
 */
typedef enum
{
    JS_ACC,     /* Accepted */
    JS_WRA,     /* Wrong Answer */
    JS_CERR,    /* Compilation Error */
    JS_MLE,     /* Memory Limit Exceeded */
    JS_TLE,     /* Time Limit Exceeded */
    JS_RTE,     /* Run Time Error */
    JS_CTLE,    /* Compiler Time Limit Exceeded */
    JS_CMLE,    /* Compiler Memory Limit Exceeded */
    JS_OLE,     /* Output Limit Exceeded */
    JS_NOCLAZZ, /* No Main Class Found */
    JS_ELANG,   /* Language not Supported */
} judge_status_t;

char const* const status_msgs[] = {
    "ACC", "WRA", "CERR", "MLE",
    "TLE", "RTE", "CTLE", "CMLE",
    "OLE", "ECLASS", "ELANG"
};

judge_status_t status;
char* file_name;
char* test_input_file;
char* acc_output_file;
char* temp_dir;
char working_dir[MAX_PATH];
double max_run_time = 10.0;
double max_compile_time = 4.0;
int64_t max_output_size = 5LL << 20;
int64_t max_mem = 256LL << 20;
int64_t max_rss = 20 << 20;
int64_t max_stack = 20 << 20;
int64_t max_compiler_mem = 256L << 20;
int max_fork = 2;
int kill_signal;
int exit_code;
int judge_pid;
char* lang;
uint64_t mem_used;
double time_taken;
extern char** environ;
char alphabet[] = "vw0ua1tbs2cr3dqy4x_ep5fo6gn7hm8il9jkx";

extern int has_main_method(char* file_name);

uint64_t max(uint64_t a, uint64_t b)
{
    uint64_t xa = (a), xb = (b);
    return xa > xb ? xa : xb;
}

void g_sleep(long sec, long nsec)
{
    sec += nsec/1000000000;
    nsec %= 1000000000;
    struct timespec reqd = {.tv_sec = sec, .tv_nsec = nsec};
    struct timespec rem = { 0, 0 };
    do {
        rem = reqd;
    } while (nanosleep(&reqd, &rem));
}

void set_exe_resources()
{
    struct rlimit r = { max_fork, max_fork };
    setrlimit(RLIMIT_NPROC, &r);
    r.rlim_cur = r.rlim_max = max_rss;
    setrlimit(RLIMIT_RSS, &r);
    r.rlim_cur = r.rlim_max = max_stack;
    setrlimit(RLIMIT_STACK, &r);
    r.rlim_cur = r.rlim_max = max_mem;
    setrlimit(RLIMIT_DATA, &r);
}

void set_compiler_resources()
{
    struct rlimit r;
    r.rlim_cur = r.rlim_max = max_compiler_mem;
    setrlimit(RLIMIT_DATA, &r);
}

int64_t get_vmem_size(int pid)
{
    kill(pid, SIGSTOP);

    static char cmdLine[128];
    static char line[256];
    char *low_addr, *high_addr, *path;
    char buf[32];

    int64_t ans = 0;

    sprintf(buf, "/proc/%d/cmdline", pid);
    FILE* stream = fopen(buf, "r");
    if (! stream)
        goto finish;

    fscanf(stream, "%[^\n]", cmdLine);
    fclose(stream);

    sprintf(buf, "/proc/%d/maps", pid);
    stream = fopen(buf, "r");
    if (! stream)
        goto finish;

    int64_t low, high;
    static char lookup[] = "0123456789abcdef";
    while (! feof(stream))
    {
        fscanf(stream, "%[^\n]\n", line);
        low = high = 0;
        char* ptr = line;
        for (; *ptr != '-'; ++ptr) {
            low = (low<<4) + strchr(lookup, *ptr)-lookup;
        }

        for (; !isspace(*++ptr);)
            high = (high<<4) + strchr(lookup, *ptr)-lookup;

        for (; !strchr("/[", *++ptr); );
        path = ptr;

        char* base_name = strrchr(cmdLine, '/')+1;
        ptr = strrchr(path, '/');

        if (path && (!strcmp(path, "[heap]") ||
            !strcmp(path, "[stack]") ||
            ptr && !strcmp(ptr+1, base_name)))
        {
            ans += high-low+1;
        }
    }
    fclose(stream);

finish:
    kill(pid, SIGCONT);
    return ans;
}

void gen_rand_string(char* buf, int size)
{
    int n = 0, s = size-1;
    char* ptr = buf;
    do {
        n = syscall(SYS_getrandom, ptr, s, GRND_NONBLOCK);
        if (n < 0) break;
        ptr += n;
        s -= n;
    } while (s > 0);
    for (int i = 0; i < size-1; ++i)
    {
        uint32_t ch = buf[i] & 0xff;
        buf[i] = alphabet[ch % ALPHABET_SIZE];
    }
    buf[size-1] = 0;
}

char* append_path(char* path, char* file)
{
    int len = strlen(path);
    char* ptr = path+len;
    if (path[len-1] != '/')
        *ptr++ = '/';
    strcpy(ptr, file);
    return path;
}

void initialize()
{
    char* ptr = stpcpy(working_dir, temp_dir);
    if (ptr[-1] != '/')
        *ptr++ = '/';
    gen_rand_string(ptr, 32);
    *(short*)(ptr+0x1f) = 0x2f;
    mkdir(working_dir, 0700);
}

judge_status_t run_compiler(char* const* args)
{
    int out[2];
    if (-1 == pipe(out)) {
        ERR("pipe failed !");
        return JS_RTE;
    }

    int pid = fork();
    if (-1 == pid)
    {
        ERR("fork failed !");
        close(out[0]);
        close(out[1]);
        return JS_RTE;
    }
    else if (! pid)
    {
        close(0);
        close(1);
        close(out[0]);
        dup2(out[1], 2);
        set_compiler_resources();
        execve(args[0], args, environ);
    }
    else
    {
        close(out[1]);

        int ret, m_stat;
        struct timeval start, end;

        kill(pid, SIGSTOP);
        struct timeval start_time, end_time;

        gettimeofday(&start_time, NULL);

        do {
            kill(pid, SIGCONT);
            g_sleep(0, DELTA_SLEEP);
            kill(pid, SIGSTOP);
            ret = wait4(pid, &m_stat, WUNTRACED, NULL);
            if (WIFEXITED(m_stat) || WIFSIGNALED(m_stat))
                break;

            gettimeofday(&end_time, NULL);
            time_taken = end_time.tv_sec+1.0E-6*end_time.tv_usec-
                start_time.tv_sec-1.0E-6*start_time.tv_usec;

            int64_t ans = get_vmem_size(pid);
            mem_used = max(mem_used, ans);
        }
        while(mem_used < max_compiler_mem && time_taken < max_compile_time);

        LOG("Memory Used : %li bytes\n", mem_used);
        LOG("Time Taken : %.6lf\n", time_taken);

        if (mem_used >= max_compiler_mem || time_taken >= max_compile_time)
        {
            kill(pid, SIGTERM);
            goto next;
        }

        static char m_buffer[256];
        ret = read(out[0], m_buffer, sizeof m_buffer);
        close(out[0]);

        kill(pid, SIGKILL);
        LOG("Err Stream <=> %.*s\n", ret, m_buffer);

        if (memmem(m_buffer, ret, "memory", 6)
            || mem_used > max_compiler_mem)
            return JS_CMLE;

next:
        if (time_taken > max_compile_time)
            return JS_CTLE;
        else if (ret)
            return JS_CERR;
        else if (ret == 0)
            return JS_ACC;
        else
            return JS_RTE;
    }
}

/*
 * Judge C Programs
 */
void judge_c()
{
    static char exe_path[MAX_PATH];
    static char output_file[MAX_PATH];

    /* Compile the file now */
    char const* compiler_args[] = {
        "/usr/bin/gcc", "-o", exe_path, file_name,
        "-w", "-lm", "-std=c11", NULL
    };

    char* ptr = stpcpy(exe_path, working_dir);
    gen_rand_string(ptr, 32);
    ptr = stpcpy(output_file, working_dir);
    gen_rand_string(ptr, 32);

    LOG("Executable : %s\n", exe_path);
    LOG("Output File : %s\n", output_file);

    LOG("[ ... Compiling ... ]\n");
    status = run_compiler((char * const*) compiler_args);

    if (status != JS_ACC)
        goto cleanup;

    LOG("[ ... Executing ... ]\n");

    int out_h = open(output_file, O_WRONLY | O_CREAT, 0666);
    int in_h = open(test_input_file, O_RDONLY);
    /* Execute the executable */
    int pid = fork();
    if (! pid)
    {
        dup2(in_h, 0);
        dup2(out_h, 1);
        dup2(out_h, 2);

        chroot(working_dir);

        set_exe_resources();
        char const* exe_args[] = { exe_path, NULL };
        execve(exe_args[0], (char * const*) exe_args, NULL);
    }
    else
    {
        close(in_h);
        close(out_h);
        /* All forks have same group id = pid of parent */
        int ret, m_stat;
        struct timeval start, end;

        kill(pid, SIGSTOP);
        struct timeval start_time, end_time;

        gettimeofday(&start_time, NULL);
        time_taken = 0;
        mem_used = 0;

        do {
            kill(pid, SIGCONT);
            g_sleep(0, DELTA_SLEEP);
            kill(pid, SIGSTOP);
            ret = wait4(pid, &m_stat, WUNTRACED, NULL);
            if (WIFEXITED(m_stat) || WIFSIGNALED(m_stat))
                break;

            gettimeofday(&end_time, NULL);
            time_taken = end_time.tv_sec+1.0E-6*end_time.tv_usec-
                start_time.tv_sec-1.0E-6*start_time.tv_usec;

            int64_t ans = get_vmem_size(pid);
            mem_used = max(mem_used, ans);
        }
        while(mem_used < max_mem && time_taken < max_run_time);

        LOG("Memory Used : %li bytes\n", mem_used);
        LOG("Time Taken : %.6lf\n", time_taken);
        LOG("Signal : %d\n", WTERMSIG(m_stat));
        LOG("Exit Code : %d\n", WEXITSTATUS(m_stat));
        kill(pid, SIGKILL);

        if (mem_used > max_mem)
        {
            status = JS_MLE;
            goto cleanup;
        }

        if (time_taken > max_run_time)
        {
            status = JS_TLE;
            goto cleanup;
        }

        if (WIFSIGNALED(m_stat))
        {
            kill_signal = WTERMSIG(m_stat);
            if (kill_signal == SIGKILL)
                status = JS_MLE;
            else
                status = JS_RTE;
            goto cleanup;
        }

        /* Check Output Limits */
        struct stat stat_buf;
        stat(output_file, &stat_buf);

        if (stat_buf.st_size > max_output_size)
        {
            status = JS_OLE;
            goto cleanup;
        }

        /* Proceed to diff */
        int diff_out[2];
        pipe(diff_out);

        int diff_pid = fork();
        if (! diff_pid)
        {
            close(0);
            close(diff_out[0]);
            dup2(diff_out[1], 1);
            char const* diff_args[] = {
                "/usr/bin/diff", "--brief",
                output_file, acc_output_file, NULL
            };
            execve(diff_args[0], (char * const*) diff_args, environ);
        }
        else
        {
            close(diff_out[1]);
            LOG("[ ... Diffing ... ]\n");
            waitpid(diff_pid, NULL, 0);
            char byte;
            int n_bytes = read(diff_out[0], &byte, 1);
            close(diff_out[0]);

            if (n_bytes == 0)
                status = JS_ACC;
            else
                status = JS_WRA;
        }
    }

cleanup:
    {
        char* cmd = 0;
        asprintf(&cmd, "rm -rf %s", working_dir);
        system(cmd);
        free(cmd);
    }
}

/*
 * C++ judge
 */

void judge_cpp()
{
    static char exe_path[MAX_PATH];
    static char output_file[MAX_PATH];

    /* Compile the file now */
    char const* compiler_args[] = {
        "/usr/bin/gcc", "-o", exe_path, file_name,
        "-w", "-lm", "-lstdc++", "-std=c++14", NULL
    };

    char* ptr = stpcpy(exe_path, working_dir);
    gen_rand_string(ptr, 32);
    ptr = stpcpy(output_file, working_dir);
    gen_rand_string(ptr, 32);

    LOG("[ ... Compiling ... ]\n");
    status = run_compiler((char * const*) compiler_args);

    if (status != JS_ACC)
        goto cleanup;

    LOG("[ ... Executing ... ]\n");

    int out_h = open(output_file, O_WRONLY | O_CREAT, 0666);
    int in_h = open(test_input_file, O_RDONLY);
    /* Execute the executable */
    int pid = fork();
    if (! pid)
    {
        dup2(in_h, 0);
        dup2(out_h, 1);
        dup2(out_h, 2);

        /* Run in a Jail ! */
        chroot(working_dir);

        set_exe_resources();
        char const* exe_args[] = { exe_path, NULL };
        execve(exe_args[0], (char * const*) exe_args, NULL);
    }
    else
    {
        close(in_h);
        close(out_h);
        /* All forks have same group id = pid of parent */
        int ret, m_stat;
        struct timeval start, end;

        kill(pid, SIGSTOP);
        struct timeval start_time, end_time;

        gettimeofday(&start_time, NULL);
        time_taken = mem_used = 0;

        do {
            kill(pid, SIGCONT);
            g_sleep(0, DELTA_SLEEP);
            kill(pid, SIGSTOP);
            ret = wait4(pid, &m_stat, WUNTRACED, NULL);
            if (WIFEXITED(m_stat) || WIFSIGNALED(m_stat))
                break;

            gettimeofday(&end_time, NULL);
            time_taken = end_time.tv_sec+1.0E-6*end_time.tv_usec-
                start_time.tv_sec-1.0E-6*start_time.tv_usec;

            int64_t ans = get_vmem_size(pid);
            mem_used = max(mem_used, ans);
        }
        while(mem_used < max_mem && time_taken < max_run_time);

        LOG("Memory Used : %li bytes\n", mem_used);
        LOG("Time Taken : %.6lf\n", time_taken);
        LOG("Signal : %d\n", WTERMSIG(m_stat));
        LOG("Exit Code : %d\n", WEXITSTATUS(m_stat));
        kill(pid, SIGKILL);

        if (mem_used > max_mem)
        {
            status = JS_MLE;
            goto cleanup;
        }

        if (time_taken > max_run_time)
        {
            status = JS_TLE;
            goto cleanup;
        }

        if (WIFSIGNALED(m_stat))
        {
            kill_signal = WTERMSIG(m_stat);
            if (kill_signal == SIGKILL)
                status = JS_MLE;
            else
                status = JS_RTE;
            goto cleanup;
        }

        /* Check Output Limits */
        struct stat stat_buf;
        stat(output_file, &stat_buf);

        if (stat_buf.st_size > max_output_size)
        {
            status = JS_OLE;
            goto cleanup;
        }

        /* Proceed to diff */
        int diff_out[2];
        pipe(diff_out);

        int diff_pid = fork();
        if (! diff_pid)
        {
            close(0);
            close(diff_out[0]);
            dup2(diff_out[1], 1);
            char const* diff_args[] = {
                "/usr/bin/diff", "--brief",
                output_file, acc_output_file, NULL
            };
            execve(diff_args[0], (char * const*) diff_args, environ);
        }
        else
        {
            close(diff_out[1]);
            LOG("[ ... Diffing ... ]\n");
            waitpid(diff_pid, NULL, 0);
            char byte;
            int n_bytes = read(diff_out[0], &byte, 1);
            close(diff_out[0]);

            if (n_bytes == 0)
                status = JS_ACC;
            else
                status = JS_WRA;
        }
    }

cleanup:
    {
        char* cmd = 0;
        asprintf(&cmd, "rm -rf %s", working_dir);
        system(cmd);
        free(cmd);
    }
}

/*
 * Java Judge
 */

void judge_java()
{
    static char output_file[MAX_PATH];
    static char stderr_file[MAX_PATH];
    char* clazz_file = 0;

    /* Compile the file now */
    char const* compiler_args[] = {
        "/usr/bin/javac", "-d", working_dir, file_name, NULL
    };

    char const* executor_args[] = {
        "/usr/bin/java", "-Xmx256M",
        "-cp", working_dir, NULL, NULL
    };

    char* ptr = stpcpy(output_file, working_dir);
    gen_rand_string(ptr, 32);
    ptr = stpcpy(stderr_file, working_dir);
    gen_rand_string(ptr, 32);

    max_compiler_mem = RLIM_INFINITY;
    LOG("[ ... Compiling ... ]\n");
    status = run_compiler((char * const*) compiler_args);

    if (status != JS_ACC)
        goto cleanup;

    DIR* directory = opendir(working_dir);
    struct dirent* entry;
    int found_main_class = 0;

    while (entry = readdir(directory))
    {
        if (entry->d_type == DT_REG)
        {
            asprintf(&clazz_file, "%s%s", working_dir, entry->d_name);
            if (has_main_method(clazz_file))
            {
                found_main_class = 1;
                break;
            }
            free(clazz_file);
        }
    }

    if (! found_main_class)
    {
        status = JS_NOCLAZZ;
        goto cleanup;
    }

    char* clazz = strdup(entry->d_name);
    LOG("[ ... %s ... ] \n", clazz);
    closedir(directory);

    int out_h = open(output_file, O_WRONLY | O_CREAT, 0666);
    int in_h = open(test_input_file, O_RDONLY);
    int err_h = open(stderr_file, O_WRONLY|O_CREAT, 0666);

    /* Execute the executable */
    int pid = fork();
    if (! pid)
    {
        dup2(in_h, 0);
        dup2(out_h, 1);
        dup2(err_h, 2);

        max_fork = -1;
        *strchr(clazz, '.') = 0;

        executor_args[4] = clazz;
        set_exe_resources();
        execve(executor_args[0], (char* const*)executor_args, NULL);
    }
    else
    {
        close(in_h);
        close(out_h);
        close(err_h);

        int ret, m_stat;
        struct timeval start, end;

        kill(pid, SIGSTOP);
        struct timeval start_time, end_time;

        gettimeofday(&start_time, NULL);
        time_taken = 0;
        mem_used = 0;

        do {
            kill(pid, SIGCONT);
            g_sleep(0, DELTA_SLEEP);
            kill(pid, SIGSTOP);

            ret = wait4(pid, &m_stat, WUNTRACED, NULL);
            gettimeofday(&end_time, NULL);

            if (WIFEXITED(m_stat) || WIFSIGNALED(m_stat))
                break;

            time_taken = end_time.tv_sec+1.0E-6*end_time.tv_usec-
                start_time.tv_sec-1.0E-6*start_time.tv_usec;

            struct timeval tmp_s, tmp_e;

            gettimeofday(&tmp_s, 0);
            int64_t ans = get_vmem_size(pid);
            mem_used = max(mem_used, ans);
            gettimeofday(&tmp_e, 0);

            time_taken -= tmp_e.tv_sec+1.0E-6*tmp_e.tv_usec-
                tmp_s.tv_sec-1.0E-6*tmp_s.tv_usec;
        }
        while(mem_used <= max_mem && time_taken <= max_run_time);

        LOG("Memory Consumed : %lu bytes\n", mem_used);
        LOG("Time Taken : %.6lf\n", time_taken);
        LOG("Signal : %d\n", WTERMSIG(m_stat));
        LOG("Exit Code : %d\n", WEXITSTATUS(m_stat));
        kill(pid, SIGKILL);

        if (mem_used > max_mem)
        {
            status = JS_MLE;
            goto cleanup;
        }

        if (time_taken > max_run_time)
        {
            status = JS_TLE;
            goto cleanup;
        }

        if (WIFSIGNALED(m_stat))
        {
            kill_signal = WTERMSIG(m_stat);
            if (kill_signal == SIGKILL)
                status = JS_MLE;
            else
                status = JS_RTE;
            goto cleanup;
        }

        /* Check for Exceptions */
        static char except_regex[] = "Exception in thread \"\\w+\" .*";
        static char e_buffer[1024];
        regex_t reg;
        regmatch_t match;

        int fd = open(stderr_file, O_RDONLY);
        int n_bytes = read(fd, e_buffer, sizeof e_buffer);
        close(fd);
        e_buffer[n_bytes] = 0;

        regcomp(&reg, except_regex, REG_EXTENDED|REG_NEWLINE);
        if (regexec(&reg, e_buffer, 1, &match, 0) == 0)
        {
            regfree(&reg);
            LOG("Err Stream <==> %.*s\n", n_bytes, e_buffer);
            status = JS_RTE;
            goto cleanup;
        }
        regfree(&reg);

        if (strstr(e_buffer, "Not enough space"))
        {
            LOG("Err Stream <==> %.*s\n", n_bytes, e_buffer);
            status = JS_MLE;
            goto cleanup;
        }

        /* Check Output Limits */
        struct stat stat_buf;
        stat(output_file, &stat_buf);

        if (stat_buf.st_size > max_output_size)
        {
            status = JS_OLE;
            goto cleanup;
        }

        /* Proceed to diff */
        int diff_out[2];
        pipe(diff_out);

        int diff_pid = fork();
        if (! diff_pid)
        {
            close(0);
            close(diff_out[0]);
            dup2(diff_out[1], 1);
            char const* diff_args[] = {
                "/usr/bin/diff", "--brief",
                output_file, acc_output_file, NULL
            };
            execve(diff_args[0], (char * const*) diff_args, environ);
        }
        else
        {
            close(diff_out[1]);
            LOG("[ ... Diffing ... ]\n");
            waitpid(diff_pid, NULL, 0);
            char byte;
            int n_bytes = read(diff_out[0], &byte, 1);
            close(diff_out[0]);

            if (n_bytes == 0)
                status = JS_ACC;
            else
                status = JS_WRA;
        }
    }

cleanup:
    {
        char* cmd = 0;
        asprintf(&cmd, "rm -rf %s", working_dir);
        system("if [ -f \"*.log\" ]; then rm hs*.log; fi");
        system(cmd);
        free(cmd);
    }
}

/*
 * Python judge
 */

void judge_python()
{
    static char src_path[MAX_PATH];
    static char output_file[MAX_PATH];
    char interpreter[] = "/usr/bin/python2";

    /* Compile the file now */
    const char* compiler_args[] = {
        interpreter, "-O", "-m",
        "py_compile", src_path, NULL
    };

    if (lang[2] == '3')
        interpreter[15] = '3';

    char* ptr = stpcpy(src_path, working_dir);
    gen_rand_string(ptr, 16);
    ptr = stpcpy(output_file, working_dir);
    gen_rand_string(ptr, 16);

    /* Copy the source file */
    int fd_out = open(src_path, O_WRONLY | O_CREAT, 0666);
    int fd_in = open(file_name, O_RDONLY);
    struct stat t_fstat;
    fstat(fd_in, &t_fstat);
    copy_file_range(fd_in, NULL, fd_out, NULL, t_fstat.st_size, 0);
    close(fd_in);
    close(fd_out);

    LOG("[ ... Compiling ... ]\n");

    chdir(working_dir);
    status = run_compiler((char * const*) compiler_args);

    if (status != JS_ACC)
        goto cleanup;

    LOG("[ ... Executing ... ]\n");

    int out_h = open(output_file, O_WRONLY | O_CREAT, 0666);
    int in_h = open(test_input_file, O_RDONLY);
    /* Execute the executable */
    int pid = fork();
    if (! pid)
    {
        dup2(in_h, 0);
        dup2(out_h, 1);
        dup2(out_h, 2);

        /* Run in a Jail ! */
        chroot(working_dir);

        set_exe_resources();
        char const* exe_args[] = { compiler_args[0], src_path, NULL };
        execve(exe_args[0], (char * const*) exe_args, NULL);
    }
    else
    {
        close(in_h);
        close(out_h);
        /* All forks have same group id = pid of parent */
        int ret, m_stat;
        struct timeval start, end;

        kill(pid, SIGSTOP);
        struct timeval start_time, end_time;

        gettimeofday(&start_time, NULL);
        time_taken = mem_used = 0;

        do {
            kill(pid, SIGCONT);
            g_sleep(0, DELTA_SLEEP);
            kill(pid, SIGSTOP);
            ret = wait4(pid, &m_stat, WUNTRACED, NULL);
            if (WIFEXITED(m_stat) || WIFSIGNALED(m_stat))
                break;

            gettimeofday(&end_time, NULL);
            time_taken = (end_time.tv_sec*1000.0+end_time.tv_usec/1000.0-
                start_time.tv_sec*1000.0-start_time.tv_usec/1000.0)/1000.0;

            int64_t ans = get_vmem_size(pid);
            mem_used = max(mem_used, ans);
        }
        while(mem_used < max_mem && time_taken < max_run_time);

        LOG("Memory Used : %li bytes\n", mem_used);
        LOG("Time Taken : %.6lf\n", time_taken);
        LOG("Signal : %d\n", WTERMSIG(m_stat));
        LOG("Exit Code : %d\n", WEXITSTATUS(m_stat));
        kill(pid, SIGKILL);

        if (mem_used > max_mem)
        {
            status = JS_MLE;
            goto cleanup;
        }

        if (time_taken > max_run_time)
        {
            status = JS_TLE;
            goto cleanup;
        }

        if (WIFSIGNALED(m_stat))
        {
            kill_signal = WTERMSIG(m_stat);
            if (kill_signal == SIGKILL)
                status = JS_MLE;
            else
                status = JS_RTE;
            goto cleanup;
        }

        static char except_regex[] = "^(.*)Error.*";
        static char e_buffer[1024];
        regex_t reg;
        regmatch_t match;

        int fd = open(output_file, O_RDONLY);
        int n_bytes = read(fd, e_buffer, sizeof e_buffer);
        close(fd);
        e_buffer[n_bytes] = 0;

        regcomp(&reg, except_regex, REG_EXTENDED|REG_NEWLINE);
        if (regexec(&reg, e_buffer, 1, &match, 0) == 0)
        {
            LOG("Err Stream <==> %.*s\n", n_bytes, e_buffer);
            int start = match.rm_so, end = match.rm_eo;
            regfree(&reg);
            if (strncmp("Memory", e_buffer+start, 6) == 0)
            {
                status = JS_MLE;
            }
            else
            {
                status = JS_RTE;
            }
            goto cleanup;
        }
        regfree(&reg);

        /* Check Output Limits */
        struct stat stat_buf;
        stat(output_file, &stat_buf);

        if (stat_buf.st_size > max_output_size)
        {
            status = JS_OLE;
            goto cleanup;
        }

        /* Proceed to diff */
        int diff_out[2];
        pipe(diff_out);

        int diff_pid = fork();
        if (! diff_pid)
        {
            close(0);
            close(diff_out[0]);
            dup2(diff_out[1], 1);
            char const* diff_args[] = {
                "/usr/bin/diff", "--brief",
                output_file, acc_output_file, NULL
            };
            execve(diff_args[0], (char * const*) diff_args, environ);
        }
        else
        {
            close(diff_out[1]);
            LOG("[ ... Diffing ... ]\n");
            waitpid(diff_pid, NULL, 0);
            char byte;
            int n_bytes = read(diff_out[0], &byte, 1);
            close(diff_out[0]);

            if (n_bytes == 0)
                status = JS_ACC;
            else
                status = JS_WRA;
        }
    }

cleanup:
    {
        char* cmd = 0;
        asprintf(&cmd, "rm -rf %s", working_dir);
        system(cmd);
        free(cmd);
    }
}


/*
 * Ruby judge
 */

void judge_ruby()
{
    static char src_path[MAX_PATH];
    static char output_file[MAX_PATH];

    /* Compile the file now */
    char const* compiler_args[] = {
        "/usr/bin/ruby", "-c", src_path, NULL
    };

    char* ptr = stpcpy(src_path, working_dir);
    gen_rand_string(ptr, 16);
    ptr = stpcpy(output_file, working_dir);
    gen_rand_string(ptr, 16);

    LOG("Executable : %s\n", src_path);
    LOG("Output File : %s\n", output_file);

    /* Copy the source file */
    int fd_out = open(src_path, O_WRONLY | O_CREAT, 0666);
    int fd_in = open(file_name, O_RDONLY);
    struct stat t_fstat;
    fstat(fd_in, &t_fstat);
    copy_file_range(fd_in, NULL, fd_out, NULL, t_fstat.st_size, 0);
    close(fd_in);
    close(fd_out);

    LOG("[ ... Compiling ... ]\n");

    chdir(working_dir);
    status = run_compiler((char * const*) compiler_args);

    if (status != JS_ACC)
        goto cleanup;

    LOG("[ ... Executing ... ]\n");

    int out_h = open(output_file, O_WRONLY | O_CREAT, 0666);
    int in_h = open(test_input_file, O_RDONLY);

    /* Execute the executable */

    int pid = fork();
    if (! pid)
    {
        dup2(in_h, 0);
        dup2(out_h, 1);
        dup2(out_h, 2);

        /* Run in a Jail ! */
        chroot(working_dir);
        max_fork = -1;
        set_exe_resources();
        char const* exe_args[] = { compiler_args[0], src_path, NULL };
        execve(exe_args[0], (char * const*) exe_args, NULL);
    }
    else
    {
        close(in_h);
        close(out_h);
        /* All forks have same group id = pid of parent */
        int ret, m_stat;
        struct timeval start, end;

        kill(pid, SIGSTOP);
        struct timeval start_time, end_time;

        gettimeofday(&start_time, NULL);
        time_taken = mem_used = 0;

        do {
            kill(pid, SIGCONT);
            g_sleep(0, DELTA_SLEEP);
            kill(pid, SIGSTOP);
            ret = wait4(pid, &m_stat, WUNTRACED, NULL);
            if (WIFEXITED(m_stat) || WIFSIGNALED(m_stat))
                break;

            gettimeofday(&end_time, NULL);
            time_taken = (end_time.tv_sec*1000.0+end_time.tv_usec/1000.0-
                start_time.tv_sec*1000.0-start_time.tv_usec/1000.0)/1000.0;

            int64_t ans = get_vmem_size(pid);
            mem_used = max(mem_used, ans);
        }
        while(mem_used < max_mem && time_taken < max_run_time);

        LOG("Memory Used : %li bytes\n", mem_used);
        LOG("Time Taken : %.6lf\n", time_taken);
        LOG("Signal : %d\n", WTERMSIG(m_stat));
        LOG("Exit Code : %d\n", WEXITSTATUS(m_stat));
        kill(pid, SIGKILL);

        if (mem_used > max_mem)
        {
            status = JS_MLE;
            goto cleanup;
        }

        if (time_taken > max_run_time)
        {
            status = JS_TLE;
            goto cleanup;
        }

        if (WIFSIGNALED(m_stat))
        {
            kill_signal = WTERMSIG(m_stat);
            if (kill_signal == SIGKILL)
                status = JS_MLE;
            else
                status = JS_RTE;
            goto cleanup;
        }

        static char except_regex[] = "[!\\(]*\\([^E]*Error.*";
        static char e_buffer[1024];
        regex_t reg;
        regmatch_t match;

        int fd = open(output_file, O_RDONLY);
        int n_bytes = read(fd, e_buffer, sizeof e_buffer);
        close(fd);
        e_buffer[n_bytes] = 0;

        regcomp(&reg, except_regex, REG_EXTENDED|REG_NEWLINE);
        if (regexec(&reg, e_buffer, 1, &match, 0) == 0)
        {
            LOG("Err Stream <==> %.*s\n", n_bytes, e_buffer);
            int start = match.rm_so, end = match.rm_eo;
            regfree(&reg);

            if (strncmp("NoMemoryError", e_buffer+start+1, 13) == 0)
            {
                status = JS_MLE;
            }
            else
            {
                status = JS_RTE;
            }
            goto cleanup;
        }
        regfree(&reg);

        /* Check Output Limits */
        struct stat stat_buf;
        stat(output_file, &stat_buf);

        if (stat_buf.st_size > max_output_size)
        {
            status = JS_OLE;
            goto cleanup;
        }

        /* Proceed to diff */
        int diff_out[2];
        pipe(diff_out);

        int diff_pid = fork();
        if (! diff_pid)
        {
            close(0);
            close(diff_out[0]);
            dup2(diff_out[1], 1);
            char const* diff_args[] = {
                "/usr/bin/diff", "--brief",
                output_file, acc_output_file, NULL
            };
            execve(diff_args[0], (char * const*) diff_args, environ);
        }
        else
        {
            close(diff_out[1]);
            LOG("[ ... Diffing ... ]\n");
            waitpid(diff_pid, NULL, 0);
            char byte;
            int n_bytes = read(diff_out[0], &byte, 1);
            close(diff_out[0]);

            if (n_bytes == 0)
                status = JS_ACC;
            else
                status = JS_WRA;
        }
    }

cleanup:
    {
        char* cmd = 0;
        asprintf(&cmd, "rm -rf %s", working_dir);
        system(cmd);
        free(cmd);
    }
}


void show_usage(char* prog_name)
{
    printf(
        "Usage : %s [options]\n\n"
        "    -f    Source code file\n"
        "    -i    Test Input file\n"
        "    -o    Correct Output file\n"
        "    -l    Language Extension\n"
        "    -d    Working directory\n"
        "    -r    Max. Execution time (sec)\n"
        "    -c    Max. Compilation time (sec)\n",
        prog_name
    );
    exit(0);
}


int main(int argc, char** argv)
{
    int ret;
    while ((ret = getopt(argc, argv, "f:i:o:l:d:r:c:h::")) != -1)
    {
        switch (ret)
        {
            case 'f':
                file_name = optarg;
                break;
            case 'i':
                test_input_file = optarg;
                break;
            case 'o':
                acc_output_file = optarg;
                break;
            case 'l':
                lang = optarg;
                break;
            case 'd':
                temp_dir = optarg;
                break;
            case 'c':
                max_compile_time = strtol(optarg, NULL, 10);
                break;
            case 'r':
                max_run_time = strtol(optarg, NULL, 10);
                break;
            default:
                show_usage(*argv);
        }
    }

    initialize();

    intptr_t* maps[] = {
        (intptr_t*) "c",     (intptr_t*) judge_c,
        (intptr_t*) "cpp",   (intptr_t*) judge_cpp,
        (intptr_t*) "java",  (intptr_t*) judge_java,
        (intptr_t*) "py2",   (intptr_t*) judge_python,
        (intptr_t*) "py3",   (intptr_t*) judge_python,
        (intptr_t*) "rb",    (intptr_t*) judge_ruby,
        NULL
    };

    intptr_t* search_ptr = (intptr_t*) maps;
    for (; *search_ptr; search_ptr += 2)
        if (0 == strcmp((char*)*search_ptr, lang))
            break;

    if (search_ptr)
        ((void (*)()) search_ptr[1])();
    else
        status = JS_ELANG;

    LOG("Status : %s\n", status_msgs[status]);
}
