#ifndef __JAVA_JUDGE__
#define __JAVA_JUDGE__

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <endian.h>
#include <stdio.h>
#include <syscall.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdint.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <string.h>

typedef uint8_t u1;
typedef uint16_t u2;
typedef uint32_t u4;
typedef uint64_t u8;

#define ACC_PUBLIC          0x0001
#define ACC_PRIVATE         0x0002
#define ACC_PROTECTED       0x0004
#define ACC_STATIC          0x0008
#define ACC_FINAL           0x0010
#define ACC_SYNCHRONIZED    0x0020
#define ACC_BRIDGE          0x0040
#define ACC_VARARGS         0x0080
#define ACC_NATIVE          0x0100
#define ACC_ABSTRACT        0x0400
#define ACC_STRICT          0x0800
#define ACC_SYNTHETIC       0x1000

#define MAIN_FLAGS          ACC_PUBLIC | ACC_STATIC

#define CP_UTF8     1
#define CP_INT      3
#define CP_FLOAT    4
#define CP_LONG     5
#define CP_DOUBLE   6
#define CP_CLASS    7
#define CP_STRING   8
#define CP_FIELD    9
#define CP_METHOD   10
#define CP_IFMETH   11
#define CP_NAME_TP  12
#define CP_METH_H   15
#define CP_METH_TP  16
#define CP_INV_DYN  18

typedef struct
__attribute__((packed)) {
    u2 name_index;
    u4 length;
    u1 info[];
} attribute_info;

typedef struct {
    u2 access_flags;
    u2 name_index;
    u2 descriptor_index;
    u2 attribute_count;
    attribute_info attributes[];
} method_info;

typedef struct
__attribute__((packed)) {
    u2 access_flags;
    u2 name_index;
    u2 desc_index;
    u2 attribute_count;
    attribute_info attributes[];
} field_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 name_index;
} cp_class_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 class_index;
    u2 name_and_type_index;
} cp_field_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 class_index;
    u2 name_and_type_index;
} cp_method_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 class_index;
    u2 name_and_type_index;
} cp_if_method_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 string_index;
} cp_string_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u4 bytes;
} cp_int_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u4 bytes;
} cp_float_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u4 high_bytes;
    u4 low_bytes;
} cp_long_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u4 high_bytes;
    u4 low_bytes;
} cp_double_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 name_index;
    u2 descriptor_index;
} cp_name_type_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 length;
    u1 bytes[];
} cp_utf8_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u1 ref_kind;
    u1 ref_index;
} cp_method_h_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 descriptor_index;
} cp_method_type_info;

typedef struct
__attribute__((packed)) {
    u1 tag;
    u2 bootstrap_method_attr_index;
    u2 name_and_type_index;
} cp_invoke_dyn_info;

typedef struct
{
    u1 tag;
    union {
        cp_class_info* c_class;
        cp_field_info* c_field;
        cp_method_info* c_method;
        cp_if_method_info* c_if_method;
        cp_string_info* c_string;
        cp_int_info* c_int;
        cp_float_info* c_float;
        cp_long_info* c_long;
        cp_double_info* c_double;
        cp_name_type_info* c_name_type;
        cp_utf8_info* c_utf8;
        cp_method_h_info* c_method_h;
        cp_method_type_info* c_method_type;
        cp_invoke_dyn_info* c_invoke_dyn;
    };
} constant_t;

char has_main_method(char const* clazz_file);
#endif
