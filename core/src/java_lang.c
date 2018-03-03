#include "java_lang.h"

char has_main_method(char const* path) 
{
	int file = open(path, O_RDONLY);
	struct stat statBuf;
	fstat(file, &statBuf);
	char* mem = (char*) mmap(NULL, statBuf.st_size, PROT_READ, MAP_PRIVATE, file, 0);

	u4 magic = *(u4*)mem;
	if (magic != 0xBEBAFECAU) {
		munmap(mem, statBuf.st_size);
		close(file);
		return -1;  // not a CLASS file
	}

	/*
	 * Class Version
	 */
	u2 major = be16toh(*(u2*)(mem+6));
	u2 minor = be16toh(*(u2*)(mem+4));

	/*
	 * Constant Pool Count
	 */
	u2 n_const_pool = be16toh(*(u2*)(mem+8));

	constant_t* pool = (constant_t*) calloc(n_const_pool, sizeof(constant_t));
	int n_strings = 0;

	/*
	 * Populate Constant Pool Information
	 */
	char* ptr = mem+10;
	for (int i = 1; i < n_const_pool; ++i)
	{
		u1 tag = *(u1*)ptr;
		pool[i].tag = tag;
		switch (tag)
		{
			case CP_UTF8:
				pool[i].c_utf8 = (cp_utf8_info*)ptr;
				++n_strings;
				ptr += 3+be16toh(pool[i].c_utf8->length);
				break;

			case CP_INT:
				pool[i].c_int = (cp_int_info*)ptr;
				ptr += sizeof(cp_int_info);
				break;
			
			case CP_FLOAT:
				pool[i].c_float = (cp_float_info*)ptr;
				ptr += sizeof(cp_float_info);
				break;

			case CP_LONG:
				pool[i].c_long = (cp_long_info*)ptr;
				ptr += sizeof(cp_long_info);
				break;

			case CP_DOUBLE:
				pool[i].c_double = (cp_double_info*)ptr;
				ptr += sizeof(cp_double_info);
				break;

			case CP_CLASS:
				pool[i].c_class = (cp_class_info*)ptr;
				ptr += sizeof(cp_class_info);
				break;

			case CP_STRING:
				pool[i].c_string = (cp_string_info*)ptr;
				ptr += sizeof(cp_string_info);
				break;

			case CP_FIELD:
				pool[i].c_field = (cp_field_info*)ptr;
				ptr += sizeof(cp_field_info);
				break;

			case CP_METHOD:
				pool[i].c_method = (cp_method_info*)ptr;
				ptr += sizeof(cp_method_info);
				break;

			case CP_IFMETH:
				pool[i].c_if_method = (cp_if_method_info*)ptr;
				ptr += sizeof(cp_if_method_info);
				break;

			case CP_NAME_TP:
				pool[i].c_name_type = (cp_name_type_info*)ptr;
				ptr += sizeof(cp_name_type_info);
				break;

			case CP_METH_H:
				pool[i].c_method_h = (cp_method_h_info*)ptr;
				ptr += sizeof(cp_method_h_info);
				break;

			case CP_METH_TP:
				pool[i].c_method_type = (cp_method_type_info*)ptr;
				ptr += sizeof(cp_method_type_info);
				break;

			case CP_INV_DYN:
				pool[i].c_invoke_dyn = (cp_invoke_dyn_info*)ptr;
				ptr += sizeof(cp_invoke_dyn_info);
		}
	}

	ptr += 6;
	u4 n_iface = be16toh(*(u2*)ptr);
	ptr += 1+n_iface<<1;

	u4 n_fields = be16toh(*(u2*)ptr);
	ptr += 2;
	for (int i = 0; i < n_fields; ++i) {
		field_info* fi = (field_info*)ptr;
		ptr += 8;
		int n_attr = be16toh(fi->attribute_count);
		for (int j = 0; j < n_attr; ++j) {
			attribute_info* atr = (attribute_info*)ptr;
			ptr += 6+be32toh(atr->length);
		}
	}

	u2 n_meth = be16toh(*(u2*)ptr);
	char found = 0;
	ptr += 2;
	for (int i = 0; i < n_meth; ++i) {
		method_info* method = (method_info*)ptr;
		u2 flags = be16toh(method->access_flags);
		if (flags & MAIN_FLAGS) {
			cp_utf8_info* m_name = pool[be16toh(method->name_index)].c_utf8;
			cp_utf8_info* m_desc = pool[be16toh(method->descriptor_index)].c_utf8;

			if (strncmp("main", m_name->bytes, 4) == 0
					&& strncmp(m_desc->bytes, "([Ljava/lang/String;)V", 22) == 0) {
				found = 1;
				break;
			}
		}
		u4 n_attr = be16toh(*(u2*)(ptr+6));
		ptr += 8;
		for (int j = 0; j < n_attr; ++j) {
			attribute_info* atr = (attribute_info*)ptr;
			ptr += 6+be32toh(atr->length);
		}
	}

	free(pool);
	munmap(mem, statBuf.st_size);
	close(file);
	return found;
}
