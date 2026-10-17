#include <stdint.h>
#include <dlfcn.h>
#include <stdlib.h>
static void(*hook)(void);static int fired;
void mattmc_test_set_after_capture_hook(void(*cb)(void)){hook=cb;}
int32_t mattmc_light_block_capture_state(void*s,const void*o,int32_t i){static int32_t(*real)(void*,const void*,int32_t);if(!real){void*h=dlopen(RUNTIME,RTLD_NOW|RTLD_GLOBAL);if(!h)abort();real=dlsym(h,"mattmc_light_block_capture_state");if(!real)abort();}int32_t status=real(s,o,i);if(status==1&&hook&&!fired){fired=1;hook();}return status;}
