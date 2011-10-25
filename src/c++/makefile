DEF_AJN_INSTALL_PATH = undefined
PATH_ERROR = \
" CodeGen error: Can not compile generated code if ALLJOYN_INSTALL_PATH\n     \
environment variable is not set.\n                                            \
Either export the ALLJOYN_INSTALL_PATH environment variable or modify the\n   \
DEF_AJN_INSTALL_PATH variable in the makefile to correctly compile and link\n \
the files"

# If the ALLJOYN_INSTALL_PATH environment variable is not defined, use the
# default value (DEF_AJN_INSTALL_PATH) which needs to be set to point to the
# AllJoyn installation directory.  If neither is set then throw an error.
#
ifeq ($(ALLJOYN_INSTALL_PATH),)
    ifeq ($(DEF_AJN_INSTALL_PATH), undefined)
        error :
		    @echo $(PATH_ERROR)
    endif 
    ALLJOYN_INSTALL_PATH = $(DEF_AJN_INSTALL_PATH)
endif 

# compiler related variables
#
CC = g++
CFLAGS = -c -Wall -pipe -std=c++98 -fno-rtti -fno-exceptions -Wno-long-long -Wno-deprecated -O3 -DNDEBUG -DQCC_OS_GROUP_POSIX -DQCC_OS_LINUX -DQCC_CPU_X86 -I$(ALLJOYN_INSTALL_PATH)/inc
LFLAGS = -s -L$(ALLJOYN_INSTALL_PATH)/lib $(ALLJOYN_INSTALL_PATH)/lib/liballjoyn.a -lrt -lstdc++ -lpthread -lcrypto

# derivative related variables (object files etc...)
#
OBJS = $(SVCOBJS) $(CLTOBJS)

# If the service files were generated, the SERVICE_GENERATED variable would be
# set at the top of the makefile, if not only make the client executable.
#
ifeq ($(SERVICE_GENERATED),)
    all : $(CLTOBJS) Client
else
    all : $(OBJS) Service Client
endif

clean:
	@echo "Cleaning object and executable files"
	@rm -f $(OBJS) Service Client
	

%.o : %.cc
	$(CC) $(CFLAGS) $<

Client : $(CLTOBJS)
	$(CC) -o Client $(CLTOBJS) $(LFLAGS)

Service : $(SVCOBJS)
	$(CC) -o Service $(SVCOBJS) $(LFLAGS) 
