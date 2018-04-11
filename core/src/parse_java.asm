format ELF64

;   +---------------------------------------------------------+
;   |                                                         |
;   |       Check if a java class file has main method        |
;   |                                                         |
;   |   The routine 'has_main_method' returns 1 iff there     |
;   |   exists some method with the following signature in    |
;   |   the CLASS file.                                       |
;   |                                                         |
;   |       public static void main(String... argv);          |
;   |                                                         |
;   |   It returns -1 if the file is not a java CLASS file    |
;   |   It returns 0 if no such main method is found          |
;   +---------------------------------------------------------+


include 'syscalls.inc'
public has_main_method


macro push_all [reg]
{
    forward push reg
}

macro pop_all [reg]
{
    reverse pop reg
}

section '.text' executable

@@:

    mov rdi, rbx
    pop rsi
    mov eax, sys_munmap
    syscall

    pop rdi
    mov eax, sys_close
    syscall

    pop_all rbx, rcx, rdx, rdi, rsi, r8, r9, r10, r12, rbp
    xor eax, eax
    ret

has_main_method:

    push_all rbx, rcx, rdx, rdi, rsi, r8, r9, r10, r12, rbp

    xor esi, esi
    mov eax, sys_open
    syscall

    push rax
    sub rsp, 144
    mov edi, eax
    mov rsi, rsp
    mov eax, sys_fstat
    syscall

    mov rsi, [rsp+48]
    add rsp, 144
    push rsi
    xor edi, edi
    mov edx, 1  ;   PROT_READ
    mov r10d, 2
    mov r8, [rsp+8]
    mov r9, rdi
    mov eax, sys_mmap
    syscall

    mov rbx, rax

;   Assuming the System is Little-Endian
;   Check if the file is a 'CLASS' file

    cmp dword [rbx], 0xbebafeca
    jnz @b

;   Get Number of Entries in Constant Pool

    mov si, [rbx+8]
    ror si, 8
    xor edi, edi
    movzx rsi, si
    push rsi

;   %rsi = 16 bytes * # Constant Pool Entries
    shl rsi, 4
    pop rax
    push rsi
    push rax

;   Allocate %rsi bytes memory space

    mov edx, 3      ;   PROT_READ or PROT_WRITE
    mov r10d, 0x22  ;   Anonymous Mapping | Private
    or r8d, -1
    mov r9, rdi
    mov eax, sys_mmap
    syscall

    pop rsi
    push rbx
    push rsi
    mov rbp, rax

    add rbx, 0xA
    mov edx, 1
    
;   Read the entire constant pool

.populate_constant_pool:

    cmp edx, [rsp]
    jz .populated_constant_pool

    mov cl, [rbx]
    movzx rcx, cl

    mov eax, edx
    cdqe
    shl eax, 4

;   rbp[rdx*16] = uint64_t[] { tag, ptr_to_info }

    mov [rbp+rax], rcx
    mov [rbp+rax+8], rbx

    inc edx
    
    cmp cl, 1
    jnz .cp_remaining

;   We have a CONSTANT_Utf8_info here

.cp_utf8:

    mov ax, [rbx+1]
    ror ax, 8
    movzx eax, ax
    cdqe
    inc rbx
    inc rbx
    inc rbx
    add rbx, rax
    jmp .populate_constant_pool

;   For other CONSTANT_[type]_info' s

.cp_remaining:

    mov al, byte [.sizes+rcx]
    movzx eax, al
    add rbx, rax
    jmp .populate_constant_pool

;   How much to increment ?
.sizes   db  0, 0, 0, 5, 5, 9, 9, 3, 3, 5, 5, 5, 5, 0, 0, 3, 3, 0, 5

;   We have populated the constant pool

.populated_constant_pool:

    add rbx, 6
    mov ax, [rbx]
    ror ax, 8
    movzx eax, ax
    inc eax
    shl eax, 1
    cdqe
    add rbx, rax

    mov ax, [rbx]
    add rbx, 2
    ror ax, 8
    movzx ecx, ax
    inc ecx

;   Its time to parse the fields

.parse_fields:

    dec ecx
    jz .parse_methods

    mov dx, [rbx+6]
    add rbx, 8
    ror dx, 8
    movzx edx, dx

    inc edx
@@:
    dec edx
    jz .parse_fields

    mov eax, [rbx+2]
    bswap eax
    cdqe
    add rbx, rax
    add rbx, 6
    jmp @b

;   Lets parse the methods now

.parse_methods:

    mov ax, [rbx]
    ror ax, 8
    add rbx, 2
    movzx ecx, ax
    inc ecx

.find_main_method:

    xor r8, r8
    dec ecx
    jz .free_pool

    mov ax, [rbx]
    ror ax, 8

;   ax = flags of the method pointed by %rbx
;   if ax == public + static then proceed to check if its main method
;   otherwise check next method

    and ax, MAIN_FLAGS
    cmp ax, MAIN_FLAGS
    jnz .get_next_method

;   We have found a 'public static' Method

    mov ax, [rbx+2]
    ror ax, 8
    movzx eax, ax
    cdqe
    mov rdx, rax
    shl rdx, 4
    mov rdx, [rbp+rdx+8]
    
;   Is the method name 'main' ?

    mov r12, rcx
    mov ecx, 4
    lea rdi, [rdx+3]
    mov rsi, szMainMethodName
    rep cmpsb

    or ecx, ecx
    mov rcx, r12

    jnz .get_next_method

;   We have a 'public static' method named 'main'
;   Its time to verify its signature
;   We need 'void (String...argv)'
;   i.e., '([Ljava/lang/String;)V'

    mov ax, [rbx+4]
    ror ax, 8
    movzx eax, ax
    cdqe
    mov rdx, rax
    shl rdx, 4
    mov rdx, [rbp+rdx+8]

    mov ecx, 22
    lea rdi, [rdx+3]
    mov rsi, szMainMethodDesc
    rep cmpsb

;   Have we found 'public static void main(String[] argv)' ?
;   If so then %r8 = 1 else %r8 = 0

    or ecx, ecx
    mov rcx, r12
    setz r8b
    jz .free_pool

.get_next_method:

    mov ax, [rbx+6]
    add rbx, 8
    ror ax, 8
    inc eax

@@:
    dec eax
    jz .find_main_method

    mov edx, [rbx+2]
    bswap edx
    add rbx, rdx
    add rbx, 6
    jmp @b

;   Deallocate all mapped pages

.free_pool:

    pop rax

    mov rdi, rbp
    mov rsi, [rsp+8]
    mov eax, sys_munmap
    syscall

    mov rdi, [rsp]
    mov rsi, [rsp+16]
    mov eax, sys_munmap
    syscall

    mov rdi, [rsp+24]
    mov eax, sys_close
    syscall

    mov eax, r8d
    add rsp, 32

    pop_all rbx, rcx, rdx, rdi, rsi, r8, r9, r10, r12, rbp
    ret


ACC_STATIC = 8
ACC_PUBLIC = 1
szMainMethodName    db  'main'
szMainMethodDesc    db  '([Ljava/lang/String;)V'
MAIN_FLAGS = ACC_PUBLIC or ACC_STATIC
