/* Diagnostic LD_PRELOAD observer. It returns unmodified libc pointers, keeps
 * its fixed bookkeeping outside measured allocations, and follows tagged
 * frees on other threads after the operation ends. Not a latency instrument.
 * Counts requested libc heap bytes, not mmap, RSS, Java heap or usable size. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <errno.h>

extern void *__libc_malloc(size_t);
extern void *__libc_calloc(size_t,size_t);
extern void *__libc_realloc(void *,size_t);
extern void __libc_free(void *);
extern void *__libc_memalign(size_t,size_t);

#define TAGS 16384
#define SLOTS (1u<<19)
/* Per category: allocations, requested bytes, frees, freed bytes, live, peak,
 * allocation failures. Category0 all,1 mattmc_rust callers,2 other callers. */
static uint64_t stats[TAGS][3][7];
struct record { uintptr_t pointer; size_t bytes; uint32_t tag,category; };
static struct record records[SLOTS];
static pthread_mutex_t lock=PTHREAD_MUTEX_INITIALIZER;
static __thread uint32_t active __attribute__((tls_model("initial-exec")));
static __thread int busy __attribute__((tls_model("initial-exec")));
static uint64_t overflow;

static size_t slot(uintptr_t pointer,int insert){
    size_t at=((pointer>>4)*11400714819323198485ull)&(SLOTS-1),tomb=SLOTS;
    for(size_t i=0;i<SLOTS;i++,at=(at+1)&(SLOTS-1)){
        uintptr_t found=records[at].pointer;
        if(found==pointer)return at;
        if(found==1&&tomb==SLOTS)tomb=at;
        if(found==0)return insert?(tomb==SLOTS?at:tomb):SLOTS;
    }
    return insert?tomb:SLOTS;
}
static unsigned category(void *caller){
    Dl_info info;
    return dladdr(caller,&info)&&info.dli_fname&&strstr(info.dli_fname,"mattmc_rust")?1:2;
}
static void add(unsigned tag,unsigned cat,size_t size){
    for(unsigned i=0;i<2;i++){
        uint64_t *s=stats[tag][i?cat:0];s[0]++;s[1]+=size;s[4]+=size;
        if(s[4]>s[5])s[5]=s[4];
    }
}
static struct record remove_record(void *pointer){
    struct record old={0};size_t at=slot((uintptr_t)pointer,0);
    if(at!=SLOTS){
        old=records[at];records[at].pointer=1;
        for(unsigned i=0;i<2;i++){
            uint64_t *s=stats[old.tag][i?old.category:0];s[2]++;s[3]+=old.bytes;s[4]-=old.bytes;
        }
    }
    return old;
}
static void install(void *pointer,size_t size,unsigned tag,unsigned cat){
    if(!tag)return;
    if(!pointer){stats[tag][0][6]++;stats[tag][cat][6]++;return;}
    size_t at=slot((uintptr_t)pointer,1);
    if(at==SLOTS){overflow++;return;}
    records[at]=(struct record){(uintptr_t)pointer,size,tag,cat};add(tag,cat,size);
}
void *malloc(size_t size){
    if(busy)return __libc_malloc(size);
    busy=1;void *p=__libc_malloc(size);
    if(active){unsigned cat=category(__builtin_return_address(0));pthread_mutex_lock(&lock);install(p,size,active,cat);pthread_mutex_unlock(&lock);}
    busy=0;return p;
}
void *calloc(size_t n,size_t size){
    if(busy)return __libc_calloc(n,size);
    busy=1;void *p=__libc_calloc(n,size);
    if(active){unsigned cat=category(__builtin_return_address(0));pthread_mutex_lock(&lock);install(p,p?n*size:0,active,cat);pthread_mutex_unlock(&lock);}
    busy=0;return p;
}
static void *aligned(size_t alignment,size_t size,void *caller){
    if(busy)return __libc_memalign(alignment,size);
    busy=1;void *p=__libc_memalign(alignment,size);
    if(active){unsigned cat=category(caller);pthread_mutex_lock(&lock);install(p,size,active,cat);pthread_mutex_unlock(&lock);}
    busy=0;return p;
}
void *memalign(size_t alignment,size_t size){return aligned(alignment,size,__builtin_return_address(0));}
void *aligned_alloc(size_t alignment,size_t size){return aligned(alignment,size,__builtin_return_address(0));}
int posix_memalign(void **out,size_t alignment,size_t size){
    if(alignment<sizeof(void *)||(alignment&(alignment-1)))return EINVAL;
    void *p=aligned(alignment,size,__builtin_return_address(0));
    if(!p)return ENOMEM;
    *out=p;return 0;
}
void free(void *p){
    if(!p)return;
    if(busy){__libc_free(p);return;}
    busy=1;pthread_mutex_lock(&lock);remove_record(p);pthread_mutex_unlock(&lock);__libc_free(p);busy=0;
}
void *realloc(void *old,size_t size){
    if(busy)return __libc_realloc(old,size);
    busy=1;unsigned cat=active?category(__builtin_return_address(0)):0;
    pthread_mutex_lock(&lock);size_t at=old?slot((uintptr_t)old,0):SLOTS;
    struct record previous=at==SLOTS?(struct record){0}:records[at];
    void *p=__libc_realloc(old,size);
    if(p||!size){if(old)remove_record(old);install(p,size,active?active:previous.tag,active?cat:previous.category);}
    else if(active){stats[active][0][6]++;stats[active][cat][6]++;}
    pthread_mutex_unlock(&lock);busy=0;return p;
}
/* Tags are single-use. The gate covers only the actual calling thread. */
int fallen_alloc_begin(int tag){
    if(tag<=0||tag>=TAGS||active)return -1;
    active=(unsigned)tag;return 0;
}
int fallen_alloc_snapshot(int tag,uint64_t *out){
    if(tag<=0||tag>=TAGS||!out)return -1;
    busy=1;pthread_mutex_lock(&lock);memcpy(out,stats[tag],sizeof(stats[tag]));out[21]=overflow;pthread_mutex_unlock(&lock);busy=0;return 0;
}
int fallen_alloc_end(int tag,uint64_t *out){
    if(active!=(unsigned)tag)return -1;
    active=0;return fallen_alloc_snapshot(tag,out);
}
/* Known-size observer controls, including a realloc, and delayed cross-thread
 * free. volatile prevents compiler elimination of these actual libc calls. */
static void *volatile held;
void fallen_alloc_control(void){
    void *volatile a=malloc(13);void *volatile b=calloc(2,7);
    a=realloc(a,31);free(a);free(b);
}
void fallen_alloc_hold(void){held=malloc(97);}
static void *release_thread(void *unused){(void)unused;free(held);held=NULL;return NULL;}
void fallen_alloc_release(void){pthread_t thread;if(pthread_create(&thread,NULL,release_thread,NULL)==0)pthread_join(thread,NULL);}
