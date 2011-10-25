# Copyright 2010 - 2011, Qualcomm Innovation Center, Inc.
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
#

# codegenttest.pl
#
# Created: November, 2010
#
# Purpose:  regression testing for code generator app; verify
# response to various invalid XML input contained in test files; verify that 
# generated code builds and runs successfully.
# 
# Usage:
# perl codegentest.pl <ajgen> <test> <unit test> <alljoyn>
#   ajgen:    path to ajgen application
#   test:      path to test file directory
#   unit test: path to unit test file directory
#   alljoyn:   ABSOLUTE path to AllJoyn 'dist' directory
#
#

use Test::More;;
use IO::All;
use File::Spec::Functions;
use Cwd;
use Cwd 'chdir';
use Forks::Super;
use POSIX;
use XML::Simple;
use Data::Compare;

use strict;
use warnings;

my $AJINSTALL;
my $ajngen;
my $TEST_DIR;
my $UNIT_TEST_DIR;
my @files;
my $filepath;
my $ajdpid = 0;
my $servicepid = 0;

# add CTRL-C handler
$SIG{INT} = \&got_ctrl_c;

unless ( -e ($ajngen = $ARGV[0]) ) {
    usage();
	BAIL_OUT("Error:  must provide a valid path to ajngen");
}

unless ( -e ($TEST_DIR = $ARGV[1]) ) {
    usage();
	BAIL_OUT("Error:  must provide a valid path to the test directory");
}

unless ( -e ($UNIT_TEST_DIR = $ARGV[2]) ) {
    usage();
	BAIL_OUT("Error:  must provide a valid path to the unit test directory");
}

unless ( -e ($AJINSTALL = $ARGV[3]) ) {
    usage();
	BAIL_OUT("Error:  must provide a valid path to AllJoyn dist directory");
}

unless ( file_name_is_absolute($AJINSTALL) ) {
    usage();
    BAIL_OUT("Error:  path to AllJoyn dist directory must be ABSOLUTE");
}

my $AJBIN = catdir($AJINSTALL, "bin");
my $AJD = catfile($AJBIN, "alljoyn-daemon");
my $AJDCMD = "$AJD --internal";

unless (-e $AJD) {
    BAIL_OUT("Error:  cannot find $AJD - check to see if AllJoyn has been built");
}

# set env var so we can run make on generated code
$ENV{ALLJOYN_INSTALL_PATH} = $AJINSTALL;

# create a directory to use for codegen output
my $codegenoutdir = "codegenout";

# need a well-known-name to pass as an arg in the cmd line
my $wkn = "my.org";

# -r switch
my $root = "TestInterface";

unless (-e $codegenoutdir) {
    mkdir $codegenoutdir or
        die "unable to create $codegenoutdir";
}

clear_directory($codegenoutdir);

my @codegenargs = ();
my $codegenout;
my $cmpstr;
my $testname;
my $use_msg_line_count;
my $use_msg_line_count_new;
my $testfile;
my $obj;
my $rootpath;
my $objpath;
my $xml;
my $expectedObjs;
my $classNames_ref;
my @classNames;
my $output_ref; 
my $actualObjs;
my $fileName;

# testcases
@codegenargs = ();
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$use_msg_line_count = scalar(@$codegenout);

$testname = "no args - first line";
$cmpstr = "AllJoyn Code Generator\n";
is($codegenout->[0], $cmpstr, $testname);

$testname = "no args - another line";
$cmpstr = "be output in the current working directory";
ok(is_contained_in($cmpstr, $codegenout), $testname);

$testname = "no args - yet another line";
$cmpstr = "files containing the xml definition of";
ok(is_contained_in($cmpstr, $codegenout), $testname);

$testname = "no args - line count";
cmp_ok($use_msg_line_count, '>', 25, $testname);

@codegenargs = qw/-h/;
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$use_msg_line_count_new = scalar(@$codegenout);
$testname = "help (-h) - first line";
$cmpstr = "AllJoyn Code Generator\n";
is($codegenout->[0], $cmpstr, $testname);

$testname = "help (-h) - line count";
is($use_msg_line_count, $use_msg_line_count_new, $testname);

@codegenargs = qw/--help/;
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$use_msg_line_count_new = scalar(@$codegenout);
$testname = "help (--help) - first line";
$cmpstr = "AllJoyn Code Generator\n";
is($codegenout->[0], $cmpstr, $testname);

$testname = "help (-help) - line count";
is($use_msg_line_count, $use_msg_line_count_new, $testname);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "bob";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "--object-path $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "object name (--object-path)";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
clear_directory($codegenoutdir);


$obj = "bo:b";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
test_error("org.ayatana.indicator.session.xml",
           "object name specified contains invalid character",
           "object name - illegal character",
           $objpath);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "bob";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "output path (-p)";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("--output-path $codegenoutdir", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "output path (--output-path)";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("--output-path nothere", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "output path is not valid";
$testname = "output path - nonexistent - error message";
ok( is_contained_in($cmpstr, $codegenout), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("--output-path $testfile", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "output path is not valid";
$testname = "output path - file instead of dir - error message";
ok( is_contained_in($cmpstr, $codegenout), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("--output-path $codegenoutdir", "-b $objpath",  
    "-w $wkn", $testfile);
get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "overwrite (-o)";
@codegenargs = ("--output-path $codegenoutdir", "-b $objpath", "-o", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
ok( all_files_generated($codegenoutdir, \@classNames, 0, 1), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("--output-path $codegenoutdir", "-b $objpath", 
    "-w $wkn", $testfile);
get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "overwrite (--overwrite)";
@codegenargs = ("--output-path $codegenoutdir", "-b $objpath", "--overwrite", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
ok( all_files_generated($codegenoutdir, \@classNames, 0, 1), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-b $objpath",  
    "--well-known-name $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "well known name (--well-known-name)";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "bob";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-b $objpath",  
    "-c", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "client files only (-c)";
ok( all_files_generated($codegenoutdir, \@classNames, 1, 0), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "org.ayatana.indicator.session.xml");
$obj = "bob";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-b $objpath", 
    "--client-only", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "client files only (--client-only)";
ok( all_files_generated($codegenoutdir, \@classNames, 1, 0), $testname);
clear_directory($codegenoutdir);


test_error("arg_with_no_name.xml", 
           "all arguments must be named",
           "arg with no name (-l NOT used)");

           
$testfile = catfile($TEST_DIR, "arg_with_no_name.xml");
@codegenargs = ("-p $codegenoutdir", "-l", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node});
@classNames = keys %$classNames_ref;
$testname = "arg with no name (-l) - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "arg with no name (-l) - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "empty_interface.xml");
$obj = "bob";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'interface\' is not complete";
$testname = "empty interface (-e NOT USED) - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "empty interface (-e NOT USED) - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "empty_interface.xml");
$obj = "bob";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-e", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'interface\' is not complete";
$testname = "empty interface (-e) - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "empty interface (-e) - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "empty interface (-e) - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "empty_nested_node.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'node\' is not complete";
$testname = "empty nested node (-e NOT USED) - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "empty nested node (-e NOT USED) - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


# The following test fails. See AJGEN-145
=pod
$testfile = catfile($TEST_DIR, "empty_nested_node.xml");
$obj = "fred";
$rootpath = "/org/freedesktop/";
$objpath = $rootpath . $obj;
@codegenargs = ("-p $codegenoutdir", "-e", "-b $objpath", 
    "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'node\' is not complete";
$testname = "empty nested node (-e) - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
@classNames = keys %$classNames_ref;
$testname = "empty nested node (-e) - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "empty nested node (-e) - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);
=cut

$testfile = catfile($TEST_DIR, "empty_node.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'node\' is not complete";
$testname = "empty node (-e NOT USED) - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "empty node (-e NOT USED) - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


# The following test fails. See AJGEN-145
=pod
$testfile = catfile($TEST_DIR, "empty_node.xml");
@codegenargs = ("-p $codegenoutdir", "-e", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'node\' is not complete";
$testname = "empty node (-e) - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node});
@classNames = keys %$classNames_ref;
$testname = "empty node (-e) - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "empty node (-e) - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);
=cut

$testfile = catfile($TEST_DIR, "empty_signal.xml");
@codegenargs = ("-p $codegenoutdir", "-e", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node});
@classNames = keys %$classNames_ref;
$testname = "empty signal (-e) - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "empty signal (-e) - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "excessive_arg_type_length.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "is not facet-valid with respect to maxLength";
$testname = "excessive arg type length - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "excessive arg type length - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "interface_with_only_annotation.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "The content of element \'interface\' is not complete";
$testname = "interface with only annotation - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "interface with only annotation - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "invalid_access_value.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "is not facet-valid with respect to enumeration";
$testname = "invalid access value - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "invalid access value - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "invalid_arg_type.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "is not facet-valid with respect to pattern";
$testname = "invalid arg type - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "invalid arg type - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "method_arg_with_dir_attr.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "is not facet-valid with respect to enumeration";
$testname = "invalid direction attr - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "invalid direction attr - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "method_arg_without_dir_attr.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node});
@classNames = keys %$classNames_ref;
$testname = "missing direction attr - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "missing direction attr - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "nested_node_with_no_name.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "Attribute \'name\' must appear on element \'node\'";
$testname = "nested node with no name - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$testname = "nested node with no name - no files generated";
ok( is_empty($codegenoutdir), $testname);
clear_directory($codegenoutdir);


$testfile = catfile($TEST_DIR, "signal_arg_with_dir_attr.xml");
@codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
$codegenout = get_codegen_output($ajngen, \@codegenargs);
$cmpstr = "Signal argument \"first_arg\" should not have a direction field";
$testname = "signal arg with direction attr - schema warning";
ok( is_contained_in($cmpstr, $codegenout), $testname);
$xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
($expectedObjs, $classNames_ref)  = 
    get_classes_and_instances_from_xml($xml->{node});
@classNames = keys %$classNames_ref;
$testname = "signal arg with direction attr - file generation";
ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
$testname = "signal arg with direction attr - make";
cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
clear_directory($codegenoutdir);


# see mbus-278 and mbus-279 for org.gnome.bluetooth.applet.xml
# see AJGEN-131 for org.freedesktop.hal.xml
my @examplefiles = qw/  org.ayatana.indicator.session.xml
                        org.freedesktop.modemmanager.xml
                        org.gnome.nautilus.xml  /;

foreach(@examplefiles) {
    $testfile = catfile($TEST_DIR, $_);
    $obj = "bob";
    $rootpath = "/org/freedesktop/";
    $objpath = $rootpath . $obj;
    @codegenargs = ("-p $codegenoutdir", "-b $objpath", 
        "-l", "-w $wkn", $testfile);
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    $xml = XML::Simple->new()->XMLin($testfile, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
    ($expectedObjs, $classNames_ref)  = 
        get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
    @classNames = keys %$classNames_ref;
    $testname = "$_ - files generated";
    ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
    $testname = "$_ - make";
    cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
    clear_directory($codegenoutdir);
}


# Test invalid class and instance name collisions
test_error("invalid_deep_nested_class_collision.xml",
           "two interfaces with the same name but different signatures",
           "invalid_deep_nested_class_collision.xml");

test_error("invalid_multiple_root_nodes.xml",
           "The markup in the document following the root element must be well-formed",
           "invalid_multiple_root_nodes.xml");

test_error("invalid_nested_class_collision.xml",
           "two interfaces with the same name but different signatures",
           "invalid_nested_class_collision.xml");

test_error("invalid_nested_noname.xml",
           "Object name of a nested object must be specified in the XML",
           "invalid_nested_noname.xml");

test_error("invalid_nested_objpath.xml",
           "Nested nodes cannot specify object paths",
           "invalid_nested_objpath.xml");

test_error("invalid_nested_same_name.xml",
           "XML file contains nested objects with the same name",
           "invalid_nested_same_name.xml");

test_error("invalid_node_collision.xml",
           "XML file contains nested objects with the same name",
           "invalid_node_collision.xml");

test_error("invalid_node_no_path.xml",
           "Object name not defined",
           "invalid_node_no_path.xml");

test_error("invalid_same_level_class_collision.xml",
           "two interfaces with the same name but different signatures",
           "invalid_same_level_class_collision.xml");

           
# Test invalid interactions between the -b option and the xml
test_error("b_tests/unnamed_base.xml",
           "Object name not defined",
           "(-b): None - XML: Unnamed Base");

test_error("b_tests/sop_base_node.xml",
           "Object name not defined",
           "(-b): None - XML: SOP Base");

$objpath = "/com/qcom/";
test_error("b_tests/unnamed_base.xml",
           "Object name not defined",
           "(-b): SOP - XML: Unnamed Base",
           $objpath);

$objpath = "/com/quic/";
test_error("b_tests/iop_base_node.xml",
           "Object path mismatch between XML and command line arguments",
           "(-b): SOP - XML: Different IOP Base",
           $objpath);

$obj = "bob";
$rootpath = "/com/qcom/";
$objpath = $rootpath . $obj;
test_error("b_tests/instance_name_base.xml",
           "class name mismatch between XML and command line arguments",
           "(-b): IOP - XML: Different Instance Name Base",
           $objpath);

$obj = "bob";
$rootpath = "/com/quic/";
$objpath = $rootpath . $obj;
test_error("b_tests/iop_base_node.xml",
           "Object path mismatch between XML and command line arguments",
           "(-b): IOP - XML: Different IOP Base",
           $objpath);

# Start Running the Automated Unit Tests
my $typetestclient = "Client";
my $typetestservice = "Service";
my $client_cmd =    catfile($codegenoutdir, $typetestclient);
my $service_cmd =   catfile($codegenoutdir, $typetestservice);
my $isFirstRunTypeTestAppsCall = 1;


$ajdpid = fork {     exec            => $AJDCMD, 
                     name            => "ajd",
                     child_fh        => "join" };

Forks::Super::pause(2);

unless ($ajdpid->is_active) {
    die "cannot start alljoyn-daemon - verify that an instance is not already running";
}


# Test valid interactions between an IOP from the -b option and the xml
@files = ("b_tests/unnamed_base.xml", "b_tests/unnamed_base_with_child.xml", 
          "b_tests/instance_name_base.xml", "b_tests/iop_base_node.xml");
foreach (@files) {
    $testfile = catfile($TEST_DIR, $_);
    $fileName = substr($_, 8);
    $obj = "BaseOne";
    $rootpath = "/com/qcom/";
    $objpath = $rootpath . $obj;
    # Use the -b option
    @codegenargs = ("-p $codegenoutdir", "-l", "-b $objpath", "-R", "-w $wkn", 
        $testfile);
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    $xml = XML::Simple->new()->XMLin($testfile, 
                                     KeyAttr=>[],
                                     ForceArray=>1,
                                     KeepRoot=>1);
    ($expectedObjs, $classNames_ref)  = 
        get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
    @classNames = keys %$classNames_ref;
    $testname = "(-b): IOP - XML: $fileName - files generated";
    ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
    $testname = "(-b): IOP - XML: $fileName - make";
    cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
    ($output_ref, $actualObjs) = 
        run_type_test_apps ($service_cmd, $client_cmd, $testfile);
    evaluate_instances($expectedObjs, $actualObjs, $testfile);
    evaluate_in_out_args($testfile, $output_ref);
    clear_directory($codegenoutdir);
}


# Test valid interactions between a SOP from the -b option and the xml
@files = ("b_tests/unnamed_base_with_child.xml", 
          "b_tests/instance_name_base.xml", "b_tests/iop_base_node.xml");
foreach (@files) {
    $testfile = catfile($TEST_DIR, $_);
    $fileName = substr($_, 8);
    $objpath = "/com/qcom/";
    # Use the -b option
    @codegenargs = ("-p $codegenoutdir", "-l", "-b $objpath", "-R", "-w $wkn", 
        $testfile);
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    $xml = XML::Simple->new()->XMLin($testfile, 
                                     KeyAttr=>[],
                                     ForceArray=>1,
                                     KeepRoot=>1);
    ($expectedObjs, $classNames_ref)  = 
        get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
    @classNames = keys %$classNames_ref;
    $testname = "(-b): SOP - XML: $fileName - files generated";
    ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
    $testname = "(-b): SOP - XML: $fileName - make";
    cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
    ($output_ref, $actualObjs) = 
        run_type_test_apps ($service_cmd, $client_cmd, $testfile);
    evaluate_instances($expectedObjs, $actualObjs, $testfile);
    evaluate_in_out_args($testfile, $output_ref);
    clear_directory($codegenoutdir);
}


# Test valid base node naming cases
@files = ("b_tests/unnamed_base_with_child.xml", "b_tests/instance_name_base.xml",
          "b_tests/iop_base_node.xml", "b_tests/sop_base_node_with_child.xml");
foreach (@files) {
    $testfile = catfile($TEST_DIR, $_);
    $fileName = substr($_, 8);
    $xml = XML::Simple->new()->XMLin($testfile, 
                                     KeyAttr=>[],
                                     ForceArray=>1,
                                     KeepRoot=>1);
    ($expectedObjs, $classNames_ref)  = 
        get_classes_and_instances_from_xml($xml->{node});
    @classNames = keys %$classNames_ref;
    @codegenargs = ("-p $codegenoutdir", "-l", "-w $wkn", "-R", $testfile);
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    $testname = "(-b): None - XML: $fileName - files generated";
    ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
    $testname = "(-b): None - XML: $fileName - make";
    cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
    ($output_ref, $actualObjs) = 
        run_type_test_apps ($service_cmd, $client_cmd, $testfile);
    evaluate_instances($expectedObjs, $actualObjs, $testfile);
    evaluate_in_out_args($testfile, $output_ref);
    clear_directory($codegenoutdir);
}


# Test Properties
$filepath = catfile($UNIT_TEST_DIR, "properties/*.xml");
@files = <${filepath}>;
foreach $testfile (@files) {
    $xml = XML::Simple->new()->XMLin($testfile, 
                                     KeyAttr=>[],
                                     ForceArray=>1,
                                     KeepRoot=>1);
    my ($expectedObjs, $classNames_ref)  = 
        get_classes_and_instances_from_xml($xml->{node});
    my @classNames = keys %$classNames_ref;
    @codegenargs = ("-p $codegenoutdir", "-l", "-w $wkn", "-R", $testfile);
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    $testname = "$testfile - files generated";
    ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
    $testname = "$testfile - make";
    cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
    my ($output_ref, $actualObjs) = 
        run_property_type_test_apps ($service_cmd, $client_cmd, $testfile);
    evaluate_instances($expectedObjs, $actualObjs, $testfile);
    evaluate_property_basic_type($testfile, $output_ref);
    clear_directory($codegenoutdir);
}


# Test Methods, Signals, a combo of both, and Classes and Instances
my @unitTestSubDirs = ("classes_and_instances/*.xml", "methods/*.xml", 
                       "signals/*.xml", "samples/*.xml");    
foreach my $subDir (@unitTestSubDirs) {                       
    $filepath = catfile($UNIT_TEST_DIR, $subDir);
    @files = <${filepath}>;
    foreach $testfile (@files) {
        $xml = XML::Simple->new()->XMLin($testfile, 
                                         KeyAttr=>[],
                                         ForceArray=>1,
                                         KeepRoot=>1);
        ($expectedObjs, $classNames_ref)  = 
            get_classes_and_instances_from_xml($xml->{node});
        @classNames = keys %$classNames_ref;
        @codegenargs = ("-p $codegenoutdir", "-l", "-w $wkn", "-R", $testfile);
        $codegenout = get_codegen_output($ajngen, \@codegenargs);
        $testname = "$testfile - files generated";
        ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
        $testname = "$testfile - make";
        cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
        ($output_ref, $actualObjs) = 
            run_type_test_apps ($service_cmd, $client_cmd, $testfile);
        evaluate_instances($expectedObjs, $actualObjs, $testfile);
        evaluate_in_out_args($testfile, $output_ref);
        clear_directory($codegenoutdir);
    }
}


# Run the the tests with an Unnamed Base Node
$filepath = catfile($UNIT_TEST_DIR, "unnamed/*.xml");
@files = <${filepath}>;
foreach $testfile (@files) {
    $obj = "bob";
    $rootpath = "/com/qcom/";
    $objpath = $rootpath . $obj;
    # Use the -b option
    @codegenargs = ("-p $codegenoutdir", "-l", "-b $objpath", "-R", "-w $wkn", 
        $testfile);
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    $xml = XML::Simple->new()->XMLin($testfile, 
                                     KeyAttr=>[],
                                     ForceArray=>1,
                                     KeepRoot=>1);
    ($expectedObjs, $classNames_ref)  = 
        get_classes_and_instances_from_xml($xml->{node}, $objpath, 1);
    @classNames = keys %$classNames_ref;
    $testname = "$testfile - files generated";
    ok( all_files_generated($codegenoutdir, \@classNames, 0, 0), $testname);
    $testname = "$testfile - make";
    cmp_ok( run_make($codegenoutdir), "==", 0, $testname);
    ($output_ref, $actualObjs) = 
        run_type_test_apps ($service_cmd, $client_cmd, $testfile);
    evaluate_instances($expectedObjs, $actualObjs, $testfile);
    evaluate_in_out_args($testfile, $output_ref);
    clear_directory($codegenoutdir);
}


Forks::Super::kill "INT", $ajdpid;
while (-1 == waitpid $ajdpid, WNOHANG) {
    Forks::Super::kill "INT", $ajdpid;
    Forks::Super::pause(2);
}

Forks::Super::Job::dispose($ajdpid);

done_testing();

sub get_codegen_output {
    my ($codegenapp, $codegenargs) = @_;
    #print "cmd line:  $codegenapp @$codegenargs\n";
    my @out = `$codegenapp @$codegenargs`;
    #print "Output: @out\n";
    return \@out;
}

sub run_make {
    my $dir = shift;
    my $curdir = getcwd();
    my $makeresult = 0;
    chdir $dir or die "Unable to chdir to $dir";
    system("make", "--silent");
    $makeresult = $? >> 8;
    chdir $curdir or die "Unable to chdir to $curdir";
    return $makeresult;
}

sub is_contained_in {
    my ($search_string, $text) = @_;
    my $foundit = 0;

    foreach(@$text) {
        # print $_;
        if ($_ =~ /$search_string/) {
            $foundit = 1;
            # print "found it:  $_\n";
            last;
        }
    }

    return $foundit;
}

sub clear_directory {
    my $dir = shift;
    die unless (-e $dir && -d $dir);
    my $io = io($dir);
    foreach ($io->all) {
        unlink $_ or 
            die "cannot delete files in $dir";
    }
}

sub is_empty {
    my $dir = shift;
    my $io = io($dir);
    my @contents = $io->all;
    my $filecount = scalar(@contents);
    # if ($filecount) {
    #    print "$_\n" foreach @contents;
    # }
    $filecount > 0 ? 0 : 1;
}

# examine the output directory and verify that all of the
# source files were generated, as expected
sub all_files_generated {
    my $dir = $_[0]; 
    my @objs = @{$_[1]}; 
    my $clientonly = $_[2]; 
    my $over = $_[3];
    my $found = 1;
    my @files = ();

    my @clientfiles = ("ClientMain.cc",
                       "BusAttachmentMgr.cc",
                       "BusAttachmentMgr.h",
                       "makefile");
    my @morefiles   = ("ServiceMain.cc");
    my @copies      = ("ClientMainCopy.cc");
    my @morecopies  = ("ServiceMainCopy.cc");

    foreach my $obj (@objs) {
        # client-only=true
        push(@clientfiles, $obj . "Client.cc");
        push(@clientfiles, $obj . "Client.h");
        push(@clientfiles, $obj . "ClientHandlers.cc");

        # client-only=false
        push(@morefiles, $obj . "Service.cc");
        push(@morefiles, $obj . "Service.h");
        push(@morefiles, $obj . "ServiceMethods.cc");

        # client-only=true && overwrite=true
        push(@copies, $obj . "ClientHandlersCopy.cc");

        # client-only=false && overwrite=true
        push(@morecopies, $obj . "ServiceMethodsCopy.cc");
    }

    # determine which files should be in the output directory
    if ($clientonly) {
        @files = @clientfiles;

        # verify that we DO NOT have morefiles
        $found &= (-e (catfile($dir, $_)) ? 0 : 1) foreach(@morefiles);

        # add copies if we are overwriting
        @files = (@files, @copies) if $over;
    } else {
        @files = (@clientfiles, @morefiles);

        # add copies if we are overwriting
        @files = (@files, @copies, @morecopies) if $over;
    }

    # verify existence of expected files
    $found &= (-e (catfile($dir, $_)) ? 1 : 0) foreach(@files);
    return $found;
}

# Used to Test Fatal Errors of the Codegen Tool
sub test_error {
    # The file located under the TEST_DIR
    my $fileName = $_[0];
    # The expected error message from the codegen tool
    my $errorMsg = $_[1];
    # The name of the test
    my $testname = $_[2];
    
    $testfile = catfile($TEST_DIR, $fileName);
    
    # Check for an optional object path parameter
    if(@_ == 4) {
        my $objPath = $_[3]; 
        @codegenargs = ("-p $codegenoutdir", "-w $wkn", "-b $objPath", $testfile);
    }
    else {
        @codegenargs = ("-p $codegenoutdir", "-w $wkn", $testfile);
    }
    $codegenout = get_codegen_output($ajngen, \@codegenargs);
    ok( is_contained_in($errorMsg, $codegenout), $testname . " - error message");
    ok( is_empty($codegenoutdir), $testname . " - no files generated");
    clear_directory($codegenoutdir);
}


sub run_type_test_apps {
    my ($service, $client, $testfile) = @_;

    $servicepid = fork {    exec            => $service, 
                            name            => "service",
                            child_fh        => "join" };
    Forks::Super::pause(1);

    my $clientpid = fork {  exec            => $client, 
                            name            => "client",
                            depend_start    => "service",
                            child_fh        => "join" };

    Forks::Super::pause(1);
    Forks::Super::kill "INT", $servicepid;

    while (-1 == waitpid $servicepid, WNOHANG) {
        Forks::Super::kill "INT", $servicepid;
        Forks::Super::pause(2);
    }

    my @clientresults = $clientpid->read_stdout();
    my @serviceresults = $servicepid->read_stdout( );

    my $testvalues;
    my $nodevalues;
#    my $foundcount = 0;
    foreach(@clientresults,@serviceresults) {
        if (/(\w+)\s(\w.+)::(\w+)\s\"(\w+)\":\s([-\w\.\[\]\(\)\{\},\/]+)/) {
            $testvalues->{$1}->{$2}->{$3}->{$4} = $5;
#            $foundcount++;
        }
        # Grab Object Names and their Object Paths
        if (/Registering\s(\w+)\sat\s((\/\w+)+)/) {
            $nodevalues->{$1} = $2;
        }
    }
    
#    Test print - uncomment to print content of testvalues hash
#
#    while (my ($onekey, $onevalue) = each (%$testvalues) ) {
#        while (my ($twokey, $twovalue) = each (%$onevalue) ) {
#            while (my ($threekey, $threevalue) = each (%$twovalue) ) {
#                print "$onekey => $twokey => $threekey => $threevalue\n";
#            }
#        }
#    }

    # dump stdout if our regex didn't match enough times
    # This might not be needed anymore
#    if ($foundcount < 2) {
#        print "There may be a problem with the client and/or service:\n";
#        print "client:\n@clientresults\n";
#        print "service:\n@serviceresults\n";
#    }
    
    # Print StdOut to RunnableOutput.txt
    my $runnableoutfile = "RunnableOutput.txt";
    my $runnableout  = "=============== $testfile ===============\n\n";
    $runnableout    .= "CLIENT:\n@clientresults\n\n";
    $runnableout    .= "SERVICE:\n@serviceresults\n";
    $runnableout    .= "------------------------------------------\n\n";
    
    if($isFirstRunTypeTestAppsCall) {
        $runnableout > io($runnableoutfile);
        $isFirstRunTypeTestAppsCall = 0;
    } else {
        $runnableout >> io($runnableoutfile);
    }

    Forks::Super::Job::dispose($clientpid, $servicepid);

    return ($testvalues, $nodevalues);
}


sub run_property_type_test_apps {
    my ($service, $client, $testfile) = @_;

    my $servicepid = fork { exec            => $service, 
                            name            => "service",
                            child_fh        => "join" };
    Forks::Super::pause(1);

    my $clientpid = fork {  exec            => $client, 
                            name            => "client",
                            depend_start    => "service",
                            child_fh        => "join" };

    Forks::Super::pause(1);
    Forks::Super::kill "INT", $servicepid;

    while (-1 == waitpid $servicepid, WNOHANG) {
        Forks::Super::kill "INT", $servicepid;
        Forks::Super::pause(2);
    }

    my @clientresults = $clientpid->read_stdout();
    my @serviceresults = $servicepid->read_stdout( );

    my $testvalues;
    my $nodevalues;
    my $foundcount = 0;
    foreach(@clientresults) {
        if (/([SG]etProperty):\s(.*)/) {
            $testvalues->{$1} = $2;
            $foundcount++;
	        #print "\n$testvalues->$1\n";
	        #print "\n$testvalues->$2\n";
        }
        # Grab Object Names and their Object Paths
        if (/Registering\s(\w+)\sat\s((\/\w+)+)/) {
            $nodevalues->{$1} = $2;
        }
    }

    # dump stdout if our regex didn't match enough times
    if ($foundcount < 6) {
        print "There may be a problem with the client and/or service:\n";
        print "client:\n@clientresults\n";
        print "service:\n@serviceresults\n";
    }

    Forks::Super::Job::dispose($clientpid, $servicepid);

    return ($testvalues, $nodevalues);
}


# evaluate output from running code built in runnable mode
# from the unit test xml files for methods with basic types.
# methods have input and out args
sub evaluate_in_out_args {
    my ($testname, $testresults) = @_;
    
    my $sent = $testresults->{'Sent'};
    my $received = $testresults->{'Received'};
    my $returned = $testresults->{'Returned'};
    my $returning = $testresults->{'Returning'};

    # Determine if there are in args, out args, or both
    my $xml = XMLin($testname, KeyAttr=>[], ForceArray=>1, KeepRoot=>1);
    my %argHash = get_num_inout_args_from_xml($xml->{node});

    foreach my $interface (keys %$sent) {
        # compare sent and received values to determine pass/fail
        while (my ($sigmethkey, $sigmethvalue) = each (%{$sent->{$interface}}) ) {
            my $numInArgs = 0;
            my $expectedNumInArgs = $argHash{$interface}{$sigmethkey}{numInArgs};
            while (my ($varkey, $varvalue) = each (%$sigmethvalue) ) {
                $numInArgs++;
                is( $received->{$interface}->{$sigmethkey}->{$varkey},  # got
                    $varvalue,                                          # expected
                    "$testname (runnable) - $sigmethkey - $varkey" );   # test name
            }
            # Evaluate the number of Input Arguments
            is($expectedNumInArgs, 
               $numInArgs, 
               "$testname (runnable) - $sigmethkey - $numInArgs/$expectedNumInArgs In Arg(s)");
        }
    }

    foreach my $interface (keys %$returning) {
        # compare returning and returned values to determine pass/fail
        while (my ($sigmethkey, $sigmethvalue) = each (%{$returning->{$interface}}) ) {
            my $numOutArgs = 0;
            my $expectedNumOutArgs = $argHash{$interface}{$sigmethkey}{numOutArgs};
            while (my ($varkey, $varvalue) = each (%$sigmethvalue) ) {
                $numOutArgs++;
                is( $returned->{$interface}->{$sigmethkey}->{$varkey},  # got
                    $varvalue,                                          # expected
                    "$testname (runnable) - $sigmethkey - $varkey" )    # test name
            }
            # Evaluate the number of Output Arguments
            is($expectedNumOutArgs,
               $numOutArgs, 
               "$testname (runnable) - $sigmethkey - $numOutArgs/$expectedNumOutArgs Out Arg(s)");
        }
    }
}

sub evaluate_property_basic_type {
    my ($testname, $testresults) = @_;
    my $testvalues;
    foreach($testresults) {
        if (/(\w+):\s(\w+)\((.*)/) {
            $testvalues->{$1}= $2;
         }
    }
    #TODO ...
}


sub evaluate_instances {
    my $expectedInstances = $_[0];
    my $actualInstances = $_[1];
    my $testfile = $_[2];
    
    # Uncomment the following to see the Instance names obtained
#    my @expectedKeys = keys %$expectedInstances;
#    my @actualKeys = keys %$actualInstances;
#    print "Expected Keys: @expectedKeys\n";
#    print "Actual Keys: @actualKeys\n";
    
    foreach my $instance (keys %$expectedInstances) {
        my $path = $expectedInstances->{$instance};
        is($actualInstances->{$instance},
           $expectedInstances->{$instance},
           "$testfile (runnable) - Registered: $instance - $path");
    }
}

# Get the the number of in and out args for each method and signal from the xml 
#    data structure returned by XML::Simple
sub get_num_inout_args_from_xml{
    my @nodeArray = @{$_[0]};
    my %argsHash = ();
    my $numInArgs;
    my $numOutArgs;
    my $memberName;

    # loop over all items in node
    foreach my $node (@nodeArray) {

        # Check if the node has an interface 
        if(exists($node->{interface})) {
            # Loop over each interface
            foreach my $interface (@{$node->{interface}}) {
                my $interfaceName = $interface->{name};         
               
                # Check if there are methods
                if(exists($interface->{method})){
                    # Loop over each method
                    foreach my $method (@{$interface->{method}}) {
                        $numInArgs = 0;
                        $numOutArgs = 0;
                        $memberName = $method->{name};

                        # Check if the method has args
                        if(exists($method->{arg})) {
                            # Check the direction of each method arg
                            foreach my $arg (@{$method->{arg}}) {
                                if (lc($arg->{direction}) eq "in") {
                                    $numInArgs++;
                                }
                                else {
                                    $numOutArgs++;
                                }
                            } # end foreach arg loop
                        }
                        else {
                            # Methods with No Args
                            $numOutArgs++;
                        }

                        if(exists $argsHash{$interfaceName}{$memberName}) {
                            my $prevNumIn = 
                                $argsHash{$interfaceName}{$memberName}{numInArgs};
                            my $prevNumOut = 
                                $argsHash{$interfaceName}{$memberName}{numOutArgs};
                            if(($prevNumIn != $numInArgs) || 
                                    ($prevNumOut != $numOutArgs)) {
                                die "ERROR: Interface Names must be Unique";
                            }
                        }
                        else {
                            $argsHash{$interfaceName}{$memberName}{numInArgs} = 
                                $numInArgs;
                            $argsHash{$interfaceName}{$memberName}{numOutArgs} = 
                                $numOutArgs;
                        }
                    } # end foreach method loop
                }

                # Check if there are Signals
                if(exists($interface->{signal})) {
                    foreach my $signal (@{$interface->{signal}}) {
                        $numInArgs = 0;
                        $memberName = $signal->{name};
                        
                        if(exists($signal->{arg})) {
                            foreach my $arg (@{$signal->{arg}}) {
                               $numInArgs++;
                            }
                        }
                        else {
                            # Signal with no Args
                            $numInArgs ++;
                        }
                         
                        if(exists $argsHash{$interfaceName}{$memberName}) {
                            my $prevNumIn = 
                                $argsHash{$interfaceName}{$memberName}{numInArgs};
                            my $prevNumOut = 
                                $argsHash{$interfaceName}{$memberName}{numOutArgs};
                            if(($prevNumIn != $numInArgs) || ($prevNumOut != 0)){
                                die "ERROR: Interface Names must be Unique";
                            }
                        }
                        else {
                            $argsHash{$interfaceName}{$memberName}{numInArgs} = 
                                $numInArgs;
                            $argsHash{$interfaceName}{$memberName}{numOutArgs} = 0;
                        }
                    }
                }

                # Check if there are properties
                if(exists($interface->{property})) {
                    # TODO: Handle Properties
                }
            } # end foreach interface loop
        }

        # If there are nested nodes, recurse on them and prepend the object
        # path of the current node to the object paths of its children
        if(exists($node->{node})) {
            my %childHash = get_num_inout_args_from_xml($node->{node});
            @argsHash{keys %childHash} = values %childHash;
        }
    }

    return (%argsHash);
}

# Get the Class Names and Instance Names from the xml data structure returned by
#    XML::Simple
# Return:
#    nodeToObjectPaths - a hash mapping node/instance names to their
#        corresponding object paths
#    classNameHash - a hash where the keys are the class names and the 
#        values are hashes that contain the interfaces (in hash form from
#        XML::Simple) that make up the class and a list of object paths 
#        of all the instances of the class.
#    instanceNameCollision - flag denoting that there was an instance name 
#        collision so all instance names are to be comprised of full object paths
#    classNameCollision - flag denoting that there was a class name 
#        collision so all class names are to be comprised of full interface names
sub get_classes_and_instances_from_xml {
    # Initializing the input Arguments to default values
    my @nodeArray = @{$_[0]};
    my $parentPath = "";
    my $instanceNameCollision = 0;
    my $classNameCollision = 0;
    my $usingBOption = 0;
    
    # Return Values    
    my $nodeToObjectPaths;
    my %classNameHash;

    # Assigning the input arguments depending on how many params were provided
    if(@_ >= 2) {
        $parentPath = $_[1];    
    }
    if(@_ >= 3) {
        # When using the B option, the value from the command line must be
        # passed into this method as the $parentPath
        $usingBOption = $_[2];
    }
    if(@_ >= 4) {
        $instanceNameCollision = $_[3];
    }
    if(@_ == 5) {
        $classNameCollision = $_[4];
    }    

    foreach my $node (@nodeArray) {
        my $objectPath = "";
        my $nodeName = "";
        
        if(exists $node->{name}) {
            $nodeName = $node->{name};
        }
        
        # Determine the current Node's Name and Object Path
        ($nodeName, $objectPath) = get_obj_name_and_path($nodeName, 
                                                         $parentPath, 
                                                         $usingBOption);
        
        # Check if the current node has an interface 
        if(exists $node->{interface}) {
            ($nodeToObjectPaths, my $collision) =
                insert_instance_name($nodeToObjectPaths,
                                     $nodeName,
                                     $objectPath,
                                     $instanceNameCollision);
            $instanceNameCollision = $instanceNameCollision || $collision;
            
            my @objectPathArray = ($objectPath);
            my ($classNameHashRef, $classCollision) =
                insert_class_name(\%classNameHash,
                                  \@{$node->{interface}},
                                  \@objectPathArray,
                                  $classNameCollision);

            %classNameHash = %{$classNameHashRef};
            $classNameCollision ||= $classCollision;
        }

        # If there are nested nodes, recurse on them
        if(exists $node->{node}) {
            my ($children, $childrenHashesRef,
                $nestedNameCollision, $nestedClassCollision) =
                get_classes_and_instances_from_xml($node->{node},
                                                   $objectPath,
                                                   0, # -b Option pnly applies to base node
                                                   $instanceNameCollision,
                                                   $classNameCollision);
            # Merge the instance names from the children
            $instanceNameCollision ||= $nestedNameCollision;

            foreach my $childName (keys %$children) {
                ($nodeToObjectPaths, $nestedNameCollision) =
                    insert_instance_name($nodeToObjectPaths,
                                         $childName,
                                         $children->{$childName},
                                         $instanceNameCollision);
                $instanceNameCollision ||= $nestedNameCollision;
            }
            
            # Merge the class names from the children
            $classNameCollision ||= $nestedClassCollision;
            my %childrenHashes = %{$childrenHashesRef};
            
            foreach my $child (keys %childrenHashes) {
                (my $classNameHashRef, $nestedClassCollision) =
                    insert_class_name(\%classNameHash,
                                      \@{$childrenHashes{$child}{interfaces}},
                                      \@{$childrenHashes{$child}{objectPaths}},
                                      $classNameCollision);
                %classNameHash = %{$classNameHashRef};
                $classNameCollision ||= $nestedClassCollision;
            }
        }
    }

    return ($nodeToObjectPaths, \%classNameHash, 
            $instanceNameCollision, $classNameCollision);
}


sub get_obj_name_and_path {
    my $nodeName = $_[0];
    my $parentPath = $_[1];
    my $usingBOption = $_[2];
    my $objectPath = "";
    
    if($parentPath eq "" || $parentPath eq "/") {
        # Current node is the base node
        if($nodeName ne "") {
            # Check the first character of the node name and prepend a '/' 
            # if needed
            my $firstChar = substr($nodeName, 0, 1);
            if($firstChar eq "/") {
                $objectPath = $nodeName;
            }
            else {
                $objectPath = "/" . $nodeName;
            }
        }
    }
    else {
        if($usingBOption) {
            ($nodeName, $objectPath) = 
                get_obj_name_and_path_with_b_option($parentPath, $nodeName);
        }
        else {
            if(substr($parentPath, -1) ne "/") {
                $objectPath = $parentPath . "/" . $nodeName;
            }
            else {
                $objectPath = $parentPath . $nodeName;
            }
        }
    }
    
    return ($nodeName, $objectPath);
}

sub get_obj_name_and_path_with_b_option{
    my $bValue = $_[0];
    my $xmlValue = $_[1];
    # The b value is either an IOP or a SOP
    my $bIsIOP = 1;
    
    my $objectPath = "";
    my $nodeName = "";
    
    if(substr($bValue, -1) eq "/") {
        # The b value is a SOP
        $bIsIOP = 0;
    }

    if($bIsIOP) {
        $objectPath = $bValue;
        my @splitPath = split(/\//, $bValue);
        $nodeName = $splitPath[@splitPath - 1];
        
        # Error Checking
        if($xmlValue ne "") {
            if($xmlValue =~ m/\//) {
                if(substr($xmlValue, -1) eq "/") {
                    # b value is an IOP and xml value is an SOP
                    if((index $bValue, $xmlValue) == -1) {
                        # xml value is not a substring of b value
                        die "ERROR: Object path mismatch between XML and command line arguments";
                    }
                }
                else {
                    # Both b value and xml value are IOPs
                    if($bValue ne $xmlValue) {
                        # IOPs dont match
                        die "ERROR: Object path mismatch between XML and command line arguments";
                    }
                }
            }
            else {
                #xml value is an only an instance name
                if($nodeName ne $xmlValue) {
                    die "ERROR: Object name mismatch between XML and command line arguments";
                }
            }
        }
    }
    else {
        if($xmlValue eq "") {
            $objectPath = $bValue;
        }
        elsif($xmlValue =~ m/\//) {
            if(substr($xmlValue, -1) eq "/") {
                # Both b value and xml value are SOPs
                if($bValue ne $xmlValue) {
                    # IOPs dont match
                    die "ERROR: Object path mismatch between XML and command line arguments";
                }
                $objectPath = $bValue;
            }
            else {
                # b value is a SOP and xml value is an IOP
                if((index $xmlValue, $bValue) == -1) {
                    # b value is not a substring of xml value
                    die "ERROR: Object path mismatch between XML and command line arguments";
                }
                $objectPath = $xmlValue;
                my @splitPath = split(/\//, $xmlValue);
                $nodeName = $splitPath[@splitPath - 1];
            }
        }
        else {
            #xml value is an only an instance name
            $objectPath = $bValue . $xmlValue;
            $nodeName = $xmlValue;
        }
    }
    
    return ($nodeName, $objectPath);
}

# Uses the interfaces that the class implements to generate the class name
sub get_class_name {
    my @interfaces = @{$_[0]};
    my $classNameCollision = $_[1];
    my $className;

    # Sort the interfaces by name
    my @sortedInterfaces = sort {lc($a->{name}) ge lc($b->{name})} @interfaces;
    foreach my $interface (@sortedInterfaces) {
        my $intName = $interface->{name};
        
        # Ignore the following 2 interfaces because they do not get generated
        # by the codegen tool
        if((lc($intName) eq "org.freedesktop.dbus.introspectable") ||
           (lc($intName) eq "org.freedesktop.dbus.properties")) {
            next;   
        }
        
        # Get the interface Name to be used in the class name
        if($classNameCollision) {
            # Replace the "." with "_" in the interface name
            $intName =~ s/\./_/g;
        }
        else {
            # Get the last section of the interface name
            my @splitInterface = split(/\./, $intName);
            $intName = $splitInterface[@splitInterface - 1];
        }
        
        # Build the class name from the names of each of the interfaces
        if(!$className) {
            $className = $intName;
        }
        else {
            $className .= "__$intName";
        }
    }
    return ($className, \@sortedInterfaces);
}

# Changes class names in a hash from their short names to their full names
#     Ex: Given a Class implementing the interface com.Foo.Bar, Bar will be the 
#     class's short name and it will be changed to the full name com_Foo_Bar
sub change_classes_to_full_names {
    my %classNameHash = %{$_[0]};

    foreach my $class (keys %classNameHash) {
        # Store the Interfaces and Object Paths
        my @interfaceList = @{$classNameHash{$class}{interfaces}};
        my @objectPaths = @{$classNameHash{$class}{objectPaths}};

        # Get the new Class name
        my ($newClassName, $interfaceListRef) = get_class_name(\@interfaceList, 1);

        # Remove the old class
        delete $classNameHash{$class};

        # Insert the new class
        push(@{$classNameHash{$newClassName}{interfaces}}, @interfaceList);
        push(@{$classNameHash{$newClassName}{objectPaths}}, @objectPaths);
    }
    return \%classNameHash;
}

# Inserts a class name into a hash along with its interfaces and the object 
# paths of the nodes/instances implementing that class
sub insert_class_name {
    my %classNameHash = %{$_[0]};
    my @interfaceList = @{$_[1]};
    my @objectPath = @{$_[2]};
    my $classNameCollision = $_[3];

    my ($className, $sortedInterfacesRef) = get_class_name(\@interfaceList,
                                                           $classNameCollision);
    @interfaceList = @{$sortedInterfacesRef};
 
    # Check for a previous class name collision
    if($classNameCollision) {

        if(exists $classNameHash{$className}) {
            # Check if the node is another instance of an existing class
            if(!(Compare(\@interfaceList, \@{$classNameHash{$className}{interfaces}}))){
                # Throw an Error if different interfaces have the same name
                die "Error: Interface names must be Unique\n";
            }
            push(@{$classNameHash{$className}{objectPaths}}, @objectPath);
        }
        else {
            push(@{$classNameHash{$className}{interfaces}}, @interfaceList);
            push(@{$classNameHash{$className}{objectPaths}}, @objectPath);
        }
    }
    else {
        # Check for a new class name collision
        if(exists $classNameHash{$className}) {
            if(Compare(\@interfaceList, \@{$classNameHash{$className}{interfaces}})){
                push(@{$classNameHash{$className}{objectPaths}}, @objectPath);
                return(\%classNameHash, $classNameCollision);
            }              

            $classNameCollision = 1;
            my $newClassHashRef = change_classes_to_full_names(\%classNameHash);
            %classNameHash = %{$newClassHashRef};
            
            my ($className, $sortedInterfacesRef) = 
                get_class_name(\@interfaceList, $classNameCollision);
            
            # If there is still a class name collision, throw and error
            if(exists $classNameHash{$className}) {
                # Throw an Error if different interfaces have the same name
                die "Error: Interface names must be Unique\n";
            }
            else {
                push(@{$classNameHash{$className}{interfaces}}, @interfaceList);
                push(@{$classNameHash{$className}{objectPaths}}, @objectPath);
            }
        }
        else {
            # There were no class name collisions
            push(@{$classNameHash{$className}{interfaces}}, @interfaceList);
            push(@{$classNameHash{$className}{objectPaths}}, @objectPath);
        }
    }
    return(\%classNameHash, $classNameCollision);
}

# Inserts an instance name and its object path into a hash
sub insert_instance_name {
    my $nodeToObjectPaths = $_[0];
    my $name = $_[1];
    my $objectPath = $_[2];
    my $instanceNameCollision = $_[3];

    # If the node is the root node, then remove the object path from the name
    if($name =~ m/\//) {
        my @splitName = split(/\//, $name);
        $name = $splitName[@splitName - 1];
    }
    
    if($instanceNameCollision) {
        # Replace the all the '\' with '_' in the object paths
        my $nodeName = substr($objectPath, 1);
        $nodeName =~ s/\//_/g;
        if(exists $nodeToObjectPaths->{$nodeName}) {
            die "ERROR: Object Paths must be Unique! Duplicate: $nodeName\n";
        }
        $nodeToObjectPaths->{$nodeName} = $objectPath;
    }
    else {
        if(exists $nodeToObjectPaths->{$name}) {
            # Instance Name Collision
            $instanceNameCollision = 1;
                 
            # Go Through the list and change all existing node names to be full
            # object paths 
            foreach my $prevNodeName (keys %$nodeToObjectPaths) {
                my $existingObjPath = $nodeToObjectPaths->{$prevNodeName};
                delete $nodeToObjectPaths->{$prevNodeName};
                my $newNodeName = substr($existingObjPath, 1);
                $newNodeName =~ s/\//_/g;
                $nodeToObjectPaths->{$newNodeName} = $existingObjPath;
            }

            my $nodeName = substr($objectPath, 1);
            $nodeName =~ s/\//_/g;
            
            # If there is a still a collision after using the object path
            # as the node name, throw an error
            if(exists $nodeToObjectPaths->{$nodeName}) {
                die "ERROR: Object Paths must be Unique! Duplicate: $nodeName\n";
            }
            $nodeToObjectPaths->{$nodeName} = $objectPath;
        }
        else {
            $nodeToObjectPaths->{$name} = $objectPath;
        }
    }
    return ($nodeToObjectPaths, $instanceNameCollision);
}


# INT (CTRL-C) signal handler
sub got_ctrl_c {
    print "\n###################### Caught CTRL-C ##########################\n";
    
    if($servicepid) {
        print "killing service...\n";
        Forks::Super::kill "INT", $servicepid;
        while (-1 == waitpid $servicepid, WNOHANG) {
            Forks::Super::kill "INT", $servicepid;
            Forks::Super::pause(2);
        }
        Forks::Super::Job::dispose($servicepid);
    }
    
    if($ajdpid) {
        print "killing daemon...\n";
        Forks::Super::kill "INT", $ajdpid;
        while (-1 == waitpid $ajdpid, WNOHANG) {
            Forks::Super::kill "INT", $ajdpid;
            Forks::Super::pause(2);
        }
        Forks::Super::Job::dispose($ajdpid);
    }
    
    print "Done!\n";
    
    exit 0;
}


sub usage {
    print "Usage:\n";    
    print "perl codegentest.pl <ajgen> <test> <unit test> <alljoyn>\n";
    print "ajgen:     path to ajgen\n";
    print "test:      path to test file dir\n";
    print "unit test: path to unit test file dir\n";
    print "alljoyn:   ABSOLUTE path to AllJoyn \'dist\' directory\n";
}
