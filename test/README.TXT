                               CODEGENTEST README

DESCRIPTION
    codegentest.pl is a testing script that will run the alljoyn codegen tool 
    with test xml files in runnable mode (-R) and compile and run the output to
    evaluate it for correctness. It will check that the correct files were 
    generated, the object instances were registered to the correct object paths,
    the correct number of arguments were printed out for each method and signal,
    and that the values sent from the client match the ones recieved by the 
    service and vice versa. It also runs tests to make sure that the command 
    line options of the codegen tool are working as expected. 
    
    The script also outputs a file called RunnableOutput.txt where it writes all
    the output of all the runnable tests it compiled and ran. Here the tester
    can manually inspect the standard out of the test files that were run if 
    desired.
                               
USAGE
    - perl codegentest.pl <ajgen> <test> <unit test> <alljoyn dist directory>
    - Example usage from the alljoyn_codegen/test directory:
        perl codegentest.pl ../ajgen xml_testcases/ unitTests/ 
            /local/mnt/workspace/alljoyn_src/build/linux/x86-64/release/dist/
    - The alljoyn daemon is not required to be stared before running the script.
      If no daemon is running, the script will start the alljoyn daemon up 
      automatically.
              
ADDING NEW TESTS
    - Currently the unit tests are organized into sub directories
        - classes_and_instances:    test files testing how the codegen tool 
                       should handle class name and instance name generation 
                       and collisions.
        - methods:     test files containing only methods 
        - signals:     test files containing only signals
        - properties:  test files containing only properties
        - samples:     test files containing a combination of methods, signals, 
                       and properties
        - unnamed:     test files with an unnamed BASE node. These are tests
                       that are expected to be run with the -b option. All 
                       nested nodes are still required to have names.
    - Every test in the unitTest directory should be valid
    - When adding new test files, simply drop them into one of the 
        subdirectories under alljoyn_codegen/test/unitTest.
    - The test xml files must be well formed (all tags and attributes defined)
    - Tests that need to be run explicitly with specific options should go into 
      the xml_testcases directory. This includes invalid tests (tests that cause
      errors)
        
PERL DEPENDENCIES
    - Test::More;
    - IO::All;
    - File::Spec::Functions;
    - Cwd;
    - Cwd 'chdir';
    - Forks::Super;
    - POSIX;
    - XML::Simple;
    - Data::Compare;

KNOWN LIMITATIONS
    - The evaluation of Properties is not fully supported yet. Tests with 
        properties will be run, but the output of the properties will not be
        evaluated for correctness.
    - The -b option is not fully supported yet. To use the -b option, you must
        explicitly create the test in the perl script. There is currently no
        command line option to tell the perl script to use the -b option. This 
        means that when using an xml test file with no name attribute specified
        for the base node, the user either has to drop the test in the 
        test/unitTest/unnamed directory, add in the name attribute in 
        the xml, or manually add in the test for that xml file.
    - See "Alljoyn CodeGen Requirements.docx" under alljoyn_codegen/doc for
        details about how the -b command line option and the xml file interacts.
    - The test xml files must have exactly 1 top-level (outtermost) node.
    - Methods, signals, and properties within the same node must have unique 
      names. This is valid for the codegen tool itself, but the perl script does 
      not associate interface members with nodes when parsing the xml for in and 
      out args so it cannot detect an interface member collision across 
      interfaces within a node.

      
  Copyright © 2011 Qualcomm Innovation Center, Inc.
  This document is licensed under the Creative Commons Attribution-ShareAlike 
  3.0 Unported License (the "CC BY-SA License"); provided,
  that (i) any source code incorporated in this documentation is licensed
           under the Apache License version 2.0
  and (ii) THIS DOCUMENTATION AND ALL INFORMATION CONTAINED HEREIN ARE
           PROVIDED ON AN "AS-IS" BASIS WITHOUT WARRANTY OF ANY KIND.

  To view a copy of this license, visit

     http://creativecommons.org/licenses/by-sa/3.0/

  or send a letter to

     Creative Commons
     171 Second Street, Suite 300
     San Francisco
     California, 94105, USA