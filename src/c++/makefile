###############################################################################
# Copyright 2010 - 2011, 2013 Qualcomm Innovation Center, Inc.
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
###############################################################################
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
CFLAGS = -c -Wall -pipe -std=c++98 -fno-rtti -fno-exceptions -Wno-long-long \
	 -Wno-deprecated -O3 -DNDEBUG -DQCC_OS_GROUP_POSIX -DQCC_OS_LINUX \
	 -DQCC_CPU_X86 -I$(ALLJOYN_INSTALL_PATH)/inc
LFLAGS = -s -L$(ALLJOYN_INSTALL_PATH)/lib \
	 $(ALLJOYN_INSTALL_PATH)/lib/libajdaemon.a \
	 $(ALLJOYN_INSTALL_PATH)/lib/liballjoyn.a \
	 -lrt -lstdc++ -lpthread -lcrypto -lssl

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
	$(CC) -o Client $(CLTOBJS) $(ALLJOYN_INSTALL_PATH)/lib/BundledDaemon.o $(LFLAGS)

Service : $(SVCOBJS)
	$(CC) -o Service $(SVCOBJS) $(ALLJOYN_INSTALL_PATH)/lib/BundledDaemon.o $(LFLAGS)
