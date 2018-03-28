#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include "java_lang.h"
#include <dirent.h>
#include <linux/random.h>
#include <alloca.h>
#include <getopt.h>
#include <ctype.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <regex.h>
#include <stdint.h>
#include <fcntl.h>

/*
 * Resource Limits
 */
#define MAX_CHILDREN    2
#define MAX_MEM         (256UL << 20)           /* MiB */
#define MAX_LANG        10
#define DEFINE_PROTO(func) char* func(uint32_t, char*, char*, char*)
#define MAX(a,b)        ((a)>(b)?(a):(b))

/*
 * Parameters
 *
 * --path, -p   : Path to Source File
 * --output, -o : Path to Output Directory
 *      Output Directory contains all test cases outputs
 * --input, -i  : Path to Input Test Cases Directory
 * --lang       : Source Language
 */

DEFINE_PROTO(run_c);
DEFINE_PROTO(run_cpp);
DEFINE_PROTO(run_java);
DEFINE_PROTO(run_py2);
DEFINE_PROTO(run_py3);
DEFINE_PROTO(run_ruby);

char* gen_temp_name();
char* run_native_compiled(char** argv, uint32_t cpu_time, char* src, char* test_in, char* out);
char* run_interpreted(char** argv, char** compile, uint32_t cpu_time, char* src, char* test_in, char* out);
void show_help(char* program);

uint32_t PAGE_SIZE;

struct lang_t
{
    char* ext;
    char* (*exec)(uint32_t n_cpu, char* file, char* input, char* output);
};

struct lang_t languages[] = {
    "c", run_c,
    "cpp", run_cpp,
    "java", run_java,
    "py2", run_py2,
    "py3", run_py3,
    "rb", run_ruby,
    0, 0, 0, 0
};


int main(int argc, char** argv)
{
    char* file;
    char* input;
    char* output;
    char* lang;
    char* program_name = *argv;
    char** dummy[] = { &file, &output, &input, &lang };
    uint32_t cpu_time = 2;

    if (argc == 1)
        show_help(*argv);

    PAGE_SIZE = sysconf(_SC_PAGE_SIZE);

    int index;
    while (1) {
        struct option opts[] = {
            "path", 1, 0, 0,
            "output", 1, 0, 0,
            "input", 1, 0, 0,
            "lang", 1, 0, 0,
            "cpu", 1, 0, 0,
            "help", 0, 0, 0x1234,
            0, 0, 0, 0
        };
        int ret = getopt_long(argc, argv, "p:o:i:l:n:h", opts, &index);
        if (ret == -1)
            break;
        if (ret == 0x1234)
            show_help(program_name);
        if (ret == 0 && index < 4) {
            *dummy[index] = optarg;
        }
        if (index == 4 && ret == 0) {
            sscanf(optarg, "%i", &cpu_time);
        }
        switch (ret) {
            case 'p':
                file = optarg;
                break;
            case 'n':
                sscanf(optarg, "%i", &cpu_time);
                break;
            case 'h':
                show_help(program_name);
            case 'o':
                output = optarg;
                break;
            case 'i':
                input = optarg;
                break;
            case 'l':
                lang = optarg;
        }
    }

    printf("[INFO] Source Code : %s\n", file);
    printf("[INFO] Test Case : %s\n", input);
    printf("[INFO] Correct Output : %s\n", output);
    printf("[INFO] Lang : %s\n", lang);

    struct lang_t* l;
    for (l = languages; l->ext; l++)
        if (0 == strcmp(l->ext, lang))
            break;

    if (! l->ext) {
        puts("[ERROR] Language not yet supported");
        fflush(stdout);
        exit(2);
    }

    if (cpu_time > 10)
        cpu_time = 10;

    printf("[INFO] CPU : %u\n", cpu_time);
    puts(l->exec(cpu_time, file, input, output));
    fflush(stdout);

    exit(0);
}

/*
 * Construct a temporary file path in /var/tmp
 */

char* gen_temp_name()
{
    char* bin = NULL;
    uint8_t buf[64];
    asprintf(&bin, "/var/tmp/");

    int n_bytes = syscall(SYS_getrandom, buf, sizeof buf, GRND_NONBLOCK);
    if (n_bytes == -1) {
        fprintf(stderr, "[ERROR] getrandom : %m\n");
        fflush(stderr);
        exit(3);
    }

    static char char_set[] = "0123456789_abcdefghijklmnopqrstuvwxyz-";
    for (int i = 0; i < n_bytes; ++i) {
        asprintf(&bin, "%s%c", bin, char_set[buf[i] % sizeof(char_set)]);
    }
    return bin;
}


/*
 * Return memory used by the process
 * text+data+stack
 */

uint64_t parse_int(char* string, off_t* off)
{
    uint64_t ans = 0;
    off64_t pos = *off;
    while (! isdigit(string[pos]))
        ++pos;
    while (isdigit(string[pos]))
        ans = ans*10+string[pos++]-0x30;
    *off = pos;
    return ans;
}

uint64_t get_vm_size(pid_t pid)
{
    char* path = NULL;
    asprintf(&path, "/proc/%d/statm", pid);

    FILE* file = fopen(path, "r");
    if (file == NULL)
        return 0;

    uint64_t text, data;
    fscanf(file ,"%*lu %*lu %*lu %lu %*lu %lu %*lu", &text, &data);
    fclose(file);

    return (text+data)*PAGE_SIZE;
}


/*
 * Run any native compiled language
 */

char* run_native_compiled(char** args, uint32_t cpu, char* src, char* input, char* output)
{
    int fd[2];
    struct stat st_buf;
    int status;

    if (pipe(fd) == -1) {
        fprintf(stderr, "[ERROR] Pipe Failed !\n");
        fflush(stderr);
        return NULL;
    }

    char** ptr = args;
    char* bin = NULL;
    for (; *ptr; ++ptr)
    {
        if (0 == strncmp(*ptr, "-o", 2))
        {
            bin = ptr[1];
            break;
        }
    }

    printf("[INFO] Executable : %s\n", bin);

    pid_t pid = fork();
    if (! pid)
    {
        close(fd[0]);
        dup2(fd[1], 1);
        dup2(fd[1], 2);
        execve(args[0], args, environ);
    }
    else
    {
        close(fd[1]);
        waitpid(pid, &status, 0);

        char temp;
        int n_bytes = read(fd[0], &temp, 1);
        close(fd[0]);

        if (n_bytes > 0)
            /* Compilation Error */
            return "CERR";
    }

    if (-1 == pipe(fd))
    {
        fprintf(stderr, "[ERROR] Pipe Failed\n");
        fflush(stderr);
        return NULL;
    }

    int test_case_fd = open(input, O_RDONLY);
    if (test_case_fd == -1)
    {
        fprintf(stderr, "[ERROR] Test Case doesn't Exist\n");
        fflush(stderr);
        return NULL;
    }

    puts("[INFO] Compiled Successfully !");

    pid = fork();
    if (! pid)
    {
        close(fd[0]);
        dup2(test_case_fd, 0);
        dup2(fd[1], 1);
        dup2(1, 2);
        struct rlimit lim;

        lim.rlim_cur = lim.rlim_max = 128UL << 20;
        if (-1 == prlimit(pid, RLIMIT_STACK, &lim, NULL)) {
            printf("[Error] RLIMIT_NPROC : %m\n");
            exit(1);
        }

        lim.rlim_cur = lim.rlim_max = cpu;
        if (-1 == prlimit(pid, RLIMIT_CPU, &lim, NULL)) {
            printf("[Error] RLIMIT_CPU : %m\n");
            exit(1);
        }
        char* args[] = { bin, NULL };
        execve(args[0], args, NULL);
    }
    else
    {
        close(fd[1]);
        close(test_case_fd);

        struct rusage usage;
        int32_t cpu_time = 0;
        uint64_t mem_size = 0;
        int ret;
        do
        {
            ret = wait4(pid, &status, WNOHANG|WUNTRACED|WCONTINUED, &usage);
            cpu_time = usage.ru_utime.tv_sec;
            printf("[CPU] %li %li\n", usage.ru_utime.tv_sec, usage.ru_stime.tv_sec);
            mem_size = MAX(mem_size, get_vm_size(pid));
        }
        while (!ret && mem_size <= MAX_MEM /* && cpu_time <= cpu */);

        printf("[INFO] Memory Consumed : %lu KiB\n", mem_size >> 10);
        printf("[INFO] CPU Time Consumed : %i\n", cpu_time);

        remove(bin);

        if (mem_size > MAX_MEM)
        {
            close(fd[0]);
            return "MLE";
        }

        if (cpu_time > cpu)
        {
            close(fd[0]);
            return "TLE";
        }

        if (WIFEXITED(status))
        {
            int gen_out_fd = fd[0];

            if (-1 == pipe(fd))
            {
                close(gen_out_fd);
                fprintf(stderr, "[ERROR] Pipe Failed\n");
                fflush(stderr);
                return NULL;
            }

            pid = fork();
            if (0 == pid)
            {
                dup2(gen_out_fd, 0);
                close(fd[0]);
                dup2(fd[1], 1);
                dup2(1, 2);
                char* args[] = { "/usr/bin/diff", "--brief", output, "-", NULL };
                execve(args[0], args, environ);
            }
            else
            {
                close(fd[1]);
                char temp;
                waitpid(pid, &status, 0);
                if (read(fd[0], &temp, 1) == 0)
                    return "ACC";
                else
                    return "WRA";
            }
        }
        else
        {
            int sig = WTERMSIG(status);
            printf("[INFO] Killed By %d\n", sig);
            fflush(stdout);
            if (sig == SIGKILL)
                return "TLE";
            else
                return "RTE";
        }
    }
}


/*
 * Run Python
 */

char* python(char** args, char** compile_flags, uint32_t cpu, char* src, char* input, char* output)
{
    int in[2], fd[2];

    if (compile_flags)
    {
        if (-1 == pipe(fd))
        {
            fprintf(stderr, "[ERROR] Pipe Failed\n");
            fflush(stderr);
            return NULL;
        }

        pid_t pid = fork();
        if (pid == 0)
        {
            //close(0);
            dup2(fd[1], 2);
            close(fd[0]);
            char* arg[] = { *args, "-mpy_compile", args[1], NULL };
            execve(*args, arg, environ);
        }
        else
        {
            int status;
            close(fd[1]);
            waitpid(pid, &status, 0);

            char buffer[1024];
            int n;
            n = read(fd[0], buffer, sizeof(buffer));
            close(fd[0]);
            if (n > 0)
            {
                printf("[ERROR_INFO] %.*s", n, buffer);
                return "CERR";
            }
        }
    }

    if (-1 == pipe(in) && -1 == pipe(fd))
    {
        fprintf(stderr, "[ERROR] Pipe Failed\n");
        fflush(stderr);
        return NULL;
    }

    int file = open(input, 0);
    if (file == -1)
    {
        fprintf(stderr, "[ERROR] File doesn't Exist\n");
        fflush(stderr);
        return NULL;
    }

    pid_t pid = fork();
    if (pid == 0)
    {
        close(in[0]);
        close(fd[0]);
        dup2(file, 0);
        dup2(in[1], 1);
        dup2(fd[1], 2);

        struct rlimit lim;
        lim.rlim_cur = lim.rlim_max = cpu;
        if (-1 == prlimit(getpid(), RLIMIT_CPU, &lim, NULL)) {
            printf("[Error] RLIMIT_CPU : %m\n");
            exit(1);
        }
        execve(*args, args, environ);
    }
    else
    {
        close(fd[1]);
        close(in[1]);

        int ret, status;
        uint64_t mem = 0;

        do
        {
            ret = waitpid(pid, &status, WNOHANG|WUNTRACED);
            if (ret > 0)
                break;
            mem = MAX(mem, get_vm_size(pid));
        } while (!ret && mem <= MAX_MEM);

        if (mem > MAX_MEM)
        {
            close(in[0]);
            return "MLE";
        }

        if (ret == -1)
        {
            close(in[0]);
            fprintf(stderr, "[ERROR] Waitpid\n");
            fflush(stderr);
            return NULL;
        }

        if (WIFEXITED(status))
        {
            char* error_text = 0;
            char temp_buffer[1024];
            int count;
            while ((count = read(fd[0], temp_buffer, sizeof(temp_buffer))) > 0)
            {
                if (error_text)
                    asprintf(&error_text, "%s%.*s", error_text, count, temp_buffer);
                else
                    asprintf(&error_text, "%.*s", count, temp_buffer);
            }
            close(fd[0]);

            char error_expression[] = "^(.*Error)";
            regex_t reg;
            regmatch_t match;
            printf("[ERROR_INFO]$ %s\n", error_text);

            if (regcomp(&reg, error_expression, REG_EXTENDED|REG_NEWLINE))
            {
                printf("[ERROR] regcomp");
                return NULL;
            }

            if (regexec(&reg, error_text, 1, &match, 0) == 0)
            {
                int start = match.rm_so, end = match.rm_eo;
                regfree(&reg);

                printf("[INFO] Match : %.*s\n", end-start, error_text+start);
                if (strncmp("Syntax", error_text+start, 6) == 0)
                    return "CERR";
                else
                    return "RTE";
            }

            regfree(&reg);

            if (pipe(fd) == -1)
            {
                fprintf(stderr, "[ERROR] Pipe failed\n");
                fflush(stdout);
                return NULL;
            }

            pid_t pid = fork();
            if (pid == 0)
            {
                close(fd[0]);
                dup2(fd[1], 1);
                dup2(in[0], 0);
                dup2(1, 2);
                char* args[] = { "/usr/bin/diff", "--brief", output, "-", NULL };
                execve(args[0], args, environ);
            }
            else
            {
                close(fd[1]);
                char temp;
                waitpid(pid, &status, 0);
                if (read(fd[0], &temp, 1) == 0)
                    return "ACC";
                else
                    return "WRA";
            }
        }
        else
        {
            int sig = WTERMSIG(status);
            if (sig == SIGKILL)
                return "TLE";
            else
                return "RTE";
        }
    }
}

char* run_interpreted(char** args, char** compile_flags, uint32_t cpu, char* src, char* input, char* output)
{
    int in[2], fd[2];

    if (compile_flags)
    {
        if (-1 == pipe(fd))
        {
            fprintf(stderr, "[ERROR] Pipe Failed\n");
            fflush(stderr);
            return NULL;
        }

        pid_t pid = fork();
        if (pid == 0)
        {
            close(0);
            dup2(fd[1], 2);

            execve(*args, compile_flags, environ);
        }
        else
        {
            int status;
            close(fd[1]);
            waitpid(pid, &status, 0);

            char buffer[1024];
            int n;
            n = read(fd[0], buffer, sizeof(buffer));
            if (n > 0)
            {
                close(fd[0]);
                return "CERR";
            }
        }
    }

    if (-1 == pipe(in) && -1 == pipe(fd))
    {
        fprintf(stderr, "[ERROR] Pipe Failed\n");
        fflush(stderr);
        return NULL;
    }

    int file = open(input, 0);
    if (file == -1)
    {
        fprintf(stderr, "[ERROR] File doesn't Exist\n");
        fflush(stderr);
        return NULL;
    }

    pid_t pid = fork();
    if (pid == 0)
    {
        close(in[0]);
        dup2(file, 0);
        dup2(in[1], 1);
        dup2(fd[1], 2);

        struct rlimit lim;
        lim.rlim_cur = lim.rlim_max = cpu;
        if (-1 == prlimit(pid, RLIMIT_CPU, &lim, NULL)) {
            printf("[Error] RLIMIT_CPU : %m\n");
            exit(1);
        }
        execve(*args, args, NULL);
    }
    else
    {
        close(file);
        close(in[1]);

        int ret, status;
        uint64_t mem = 0;

        do
        {
            ret = waitpid(pid, &status, WNOHANG);
            if (ret > 0)
                break;
            mem = get_vm_size(pid);
        } while (!ret && mem <= MAX_MEM);

        if (mem > MAX_MEM)
        {
            close(in[0]);
            return "MLE";
        }

        if (ret == -1)
        {
            close(in[0]);
            fprintf(stderr, "[ERROR] Waitpid\n");
            fflush(stderr);
            return NULL;
        }

        if (WIFEXITED(status))
        {
            if (pipe(fd) == -1)
            {
                fprintf(stderr, "[ERROR] Pipe failed\n");
                fflush(stdout);
                close(fd[0]);
                return NULL;
            }

            pid_t pid = fork();
            if (pid == 0)
            {
                close(fd[0]);
                dup2(fd[1], 1);
                dup2(in[0], 0);
                dup2(1, 2);
                char* args[] = { "/usr/bin/diff", "--brief", output, "-", NULL };
                execve(args[0], args, environ);
            }
            else
            {
                close(fd[1]);
                char temp;
                waitpid(pid, &status, 0);
                if (read(fd[0], &temp, 1) == 0)
                    return "ACC";
                else
                    return "WRA";
            }
        }
        else
        {
            int sig = WTERMSIG(status);
            if (sig == SIGKILL)
                return "TLE";
            else
                return "RTE";
        }
    }
}


/*
 * Judge a C program
 */
char* run_c(uint32_t cpu, char* src, char* input, char* output)
{
    char* bin = gen_temp_name();
    char* args[] = {
        "/usr/bin/gcc",
        "-O2", "-w",
        "-std=c11",
        "-D_ONLINE_JUDGE_=1",
        "-o",
        bin, src, "-lm", NULL
    };
    char* res = run_native_compiled(args, cpu, src, input, output);
    free(bin);
    return res;
}

/*
 * Judge C++ program
 */

char* run_cpp(uint32_t cpu, char* src, char* input, char* output)
{
    char* bin = gen_temp_name();
    char* args[] = {
        "/usr/bin/gcc",
        "-O2", "-w", "-std=c++14",
        "-D_ONLINE_JUDGE_=1", "-o", bin,
        src, "-lm", "-lstdc++", NULL
    };
    char* res = run_native_compiled(args, cpu, src, input, output);
    free(bin);
    return res;
}

/*
 * Judge a Java program
 */

char* run_java(uint32_t cpu, char* src, char* input, char* output)
{
    /*
     * Create a unique directory in /var/tmp
     * unlike creating a new file in other languages
     */
    char* dir_path = gen_temp_name();
    if (mkdir(dir_path, 0775) == -1)
    {
        fprintf(stderr, "[ERROR] mkdir failed !\n");
        fflush(stderr);
        return NULL;
    }

    printf("[INFO] Dir Path : %s\n", dir_path);

    int fd[2];
    if (pipe(fd) == -1)
    {
        rmdir(dir_path);
        free(dir_path);
        fprintf(stderr, "[ERROR] pipe failed!\n");
        fflush(stderr);
        return NULL;
    }

    pid_t pid = fork();
    if (pid == 0)
    {
        close(fd[0]);
        dup2(fd[1], 1);
        dup2(fd[1], 2);

        char* args[] = { "/usr/bin/javac", "-d", dir_path, src, NULL };
        execve(*args, args, NULL);
    }
    else
    {
        int status;
        close(fd[1]);

        if (-1 == waitpid(pid, &status, 0))
        {
            rmdir(dir_path);
            free(dir_path);
            close(fd[0]);
            fprintf(stderr, "[ERROR] waitpid failed!\n");
            fflush(stderr);
            return NULL;
        }

        if (WIFSIGNALED(status))
        {
            rmdir(dir_path);
            free(dir_path);
            close(fd[0]);
            fprintf(stderr, "[COMPILER] Killed By %d", WTERMSIG(status));
            fflush(stderr);
            return NULL;
        }

        char temp;
        int n_bytes = read(fd[0], &temp, 1);
        close(fd[0]);
        if (n_bytes >= 1)
        {
            rmdir(dir_path);
            free(dir_path);
            return "CERR";
        }

        /*
         * If the control reaches here, it implies
         * that the source is successfully compiled with class files
         * present in $dir_path
         */

        /*
         * Now since a java program can have atleast one class
         * in a single file, we have to find the class that has
         * a method of the following signature
         *
         * public static void main(String[] argv);
         *
         */

        char *ptr, *main_class = NULL;
        DIR* dir = opendir(dir_path);

        if (dir == NULL)
        {
            char* rm_cmd = NULL;
            asprintf(&rm_cmd, "rm -r %s", dir_path);
            system(rm_cmd);
            free(rm_cmd);
            fprintf(stderr, "[ERROR] diropen failed!\n");
            fflush(stderr);
            return NULL;
        }

        struct dirent* entry;
        while (entry = readdir(dir))
        {
            if ((ptr = strrchr(entry->d_name, '.')) && strncmp(ptr+1, "class", 5) == 0)
            {
                char* path_file = NULL;
                asprintf(&path_file, "%s/%s", dir_path, entry->d_name);

                if (has_main_method(path_file))
                {
                    char* ptr = strrchr(entry->d_name, '.');
                    asprintf(&main_class, "%.*s", (int) (ptr-entry->d_name), entry->d_name);
                    break;
                }

                free(path_file);
            }
        }

        closedir(dir);

        if (main_class == NULL)
        {
            /* No Runnable Classes */
            char* rm_cmd = NULL;
            asprintf(&rm_cmd, "rm -r %s", dir_path);
            system(rm_cmd);
            free(rm_cmd);
            return "EMAIN";
        }

        printf("[INFO] Main Class : %s\n", main_class);

        /*
         * Now that we have the main class, lets try running it
         * Giving 256 MiB to the JVM ...
         */
        const uint64_t JVM_MAX_MEM = (256UL << 20) + MAX_MEM;

        if (pipe(fd) == -1)
        {
            fprintf(stderr, "[ERROR] pipe failed!\n");
            fflush(stderr);
            return NULL;
        }

        int test_case_fd = open(input, 0);
        pid_t pid = fork();

        if (pid == 0)
        {
            close(fd[0]);
            dup2(test_case_fd, 0);
            dup2(fd[1], 1);
            dup2(1, 2);

            struct rlimit lim;

            lim.rlim_cur = lim.rlim_max = cpu;
            if (-1 == prlimit(pid, RLIMIT_CPU, &lim, NULL)) {
                printf("[Error] RLIMIT_CPU : %m\n");
                exit(1);
            }

            chdir(dir_path);
            char* args[] = { "/usr/bin/java", "-Xms128m", "-Xmx128m", main_class, NULL };
            execve(*args, args, NULL);
        }
        else
        {
            close(fd[1]);
            int ret;
            char* cmd = NULL;
            asprintf(&cmd, "cat /proc/%d/statm", pid);
            uint64_t mem = 0;
            do
            {
                ret = waitpid(pid, &status, WNOHANG|WUNTRACED);
                //if (WIFEXITED(status) || WIFSIGNALED(status))
                //  break;
                system(cmd);
                mem = get_vm_size(pid);
                system(cmd);
                printf("[Info] Memory : %lu\n", mem/PAGE_SIZE);
            } while (!ret && mem <= JVM_MAX_MEM);

            printf("[INFO] Memory Consumed : %lu\n", mem);
            printf("[JVM] Max Memory : %lu\n", JVM_MAX_MEM);

            if (mem > JVM_MAX_MEM)
            {
                close(fd[0]);
                char* rm_cmd = NULL;
                asprintf(&rm_cmd, "rm -r %s", dir_path);
                system(rm_cmd);
                free(rm_cmd);
                return "MLE";
            }

            if (WIFEXITED(status))
            {
                int out_fd = fd[0];

                if (pipe(fd) == -1)
                {
                    fprintf(stderr, "[ERROR] pipe failed!\n");
                    fflush(stderr);
                    close(out_fd);
                    char* rm_cmd = NULL;
                    asprintf(&rm_cmd, "rm -r %s", dir_path);
                    system(rm_cmd);
                    free(rm_cmd);
                    return NULL;
                }

                pid_t diff_pid = fork();
                if (diff_pid == 0)
                {
                    close(fd[0]);
                    dup2(fd[1], 1);
                    dup2(1, 2);
                    dup2(out_fd, 0);

                    char* diff_args[] = { "/usr/bin/diff", "--brief", "-", output, NULL };
                    execve(*diff_args, diff_args, environ);
                }
                else
                {
                    close(fd[1]);
                    close(out_fd);

                    if (waitpid(diff_pid, &status, 0) == -1)
                    {
                        fprintf(stderr, "[ERROR] waitpid failed!\n");
                        fflush(stderr);
                        char* rm_cmd = NULL;
                        asprintf(&rm_cmd, "rm -r %s", dir_path);
                        system(rm_cmd);
                        free(rm_cmd);
                        return NULL;
                    }

                    if (WIFSIGNALED(status))
                    {
                        char* rm_cmd = NULL;
                        asprintf(&rm_cmd, "rm -r %s", dir_path);
                        system(rm_cmd);
                        free(dir_path);
                        close(fd[0]);
                        fprintf(stderr, "[CHECKER] Killed By %d", WTERMSIG(status));
                        fflush(stderr);
                        return NULL;
                    }

                    char temp;
                    int n_bytes = read(fd[0], &temp, 1);
                    close(fd[0]);

                    char* rm_cmd = NULL;
                    asprintf(&rm_cmd, "rm -r %s", dir_path);
                    //system(rm_cmd);
                    free(dir_path);

                    if (n_bytes == 0)
                        return "ACC";
                    else
                        return "WRA";
                }
            }
            else
            {
                int sig = WTERMSIG(status);
                if (sig == SIGKILL)
                    return "TLE";
                else
                    return "RTE";
            }
        }
    }
}


char* run_py2(uint32_t cpu, char* src, char* input, char* output)
{
    char* args[] = { "/usr/bin/python2", src, NULL };
    char* compile[] = { "-m py_compile", src, NULL };
    char* res = python(args, compile, cpu, src, input, output);
    return res;
}


char* run_py3(uint32_t cpu, char* src, char* input, char* output)
{
    char* args[] = { "/usr/bin/python3", src, NULL };
    char* compile[] = { "-m py_compile", src, NULL };
    char* res = python(args, compile, cpu, src, input, output);
    return res;
}


char* run_ruby(uint32_t cpu, char* src, char* input, char* output)
{
    char* args[] = { "/usr/bin/ruby", src, NULL };
    char* cmd[] = { "-c", src, NULL };
    char* res = run_interpreted(args, cmd, cpu, src, input, output);
    return res;
}

__attribute__((noreturn))
void show_help(char* prog)
{
    printf(
            "Usage : %s [options]\n"
            "\t--path, -p\t: Path of source file\n"
            "\t--input, -i\t: Path of test case\n"
            "\t--output, -o\t: Path of correct answer\n"
            "\t--lang, -l\t: Language of source code\n"
            "\t--cpu, -n\t: Time Limit (in seconds)\n"
            "\n\t--help, -h\t: Show this message\n",
            prog
          );
    exit(0);
}
