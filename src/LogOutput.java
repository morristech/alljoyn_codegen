/*******************************************************************************
 * Copyright 2010 - 2011, Qualcomm Innovation Center, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 ******************************************************************************/

/** ****************************************************************************
 * This file consolidates the logging output for the rest of the tool.
 * It provides a configurable logging level.  Every class that wants to generate
 * log messaged is expected to have an instance of this class which is
 * initialized with the name of the calling class.
 *
 * @author mlioy
 *
 ******************************************************************************/
public class LogOutput {
    /** ------------------------------------------------------------------------
     * enumeration describing log output levels
     */
    public enum LogLevel {
        fatal,   // Only Fatal (errors that stop execution) are logged
        error,   // Errors and above are included
        warning, // Warnings and above are displayed (this is the default)
        inform,  // log more messages, including debug
        detail   // log everything! (detailed debug, e.g. loop count)
    }


    /** ------------------------------------------------------------------------
     * data member containing  the current log output level - default is warning
     * this is a singleton value: all instances of this object share this value
     -------------------------------------------------------------------------*/
    static private LogLevel outputLevel = LogLevel.warning;


    /** ------------------------------------------------------------------------
     * data member indicating the log level that will cause a failure.
     * Set method defines the semantics.  Default value is 'fatal'
     -------------------------------------------------------------------------*/
    static private LogLevel failOnLogLevel = LogLevel.fatal;


    /** ------------------------------------------------------------------------
     * data member that contains the name of the executable - is static as this
     * should be consistent for all instances of this class.
     -------------------------------------------------------------------------*/
    static private String exeName = null;


    /** ------------------------------------------------------------------------
     * data member that contains the name of the class that is calling the
     * logging methods
     -------------------------------------------------------------------------*/
    private String callingClass = null;


    /** ------------------------------------------------------------------------
     * The constructor for the class.  It takes the name of the class it is
     * instantiated in.  This allows for the calling class to be included in
     * the log message that is output.
     *
     * @param className: name of the calling class.
     -------------------------------------------------------------------------*/
    public LogOutput(String className)
    {
        if(exeName == null)
        {
            System.out.println("Calling Logger intialization from "
                               + className
                               + " before it has been called from top level");
            System.exit(1);
        }
        callingClass = className;
    } /* LogOutput() */

    /** ------------------------------------------------------------------------
     * The constructor for the class.  It takes the name of the class it is
     * instantiated in.  This allows for the calling class to be included in
     * the log message that is output.  Also takes the name of the executable
     * such that it too can be included in the logging messages.
     *
     * @param className: name of the calling class
     * @param progName: name of the program it is a part of
     -------------------------------------------------------------------------*/
    public LogOutput(String className, String progName)
    {
        if(exeName != null)
        {
            System.out.println("Trying to reintialize Logger from "
                               + className
                               + " when it has already been done");
            System.exit(1);
        }
        callingClass = className;
        exeName = progName;
    } /* LogOutput() */


    /** ------------------------------------------------------------------------
     * Method to change the current logging level.  this will output a message
     * based on the previous log level setting.
     *
     * @param newLevel: the new log level
     * @return: 0 on success, -1 on failure     -------------------------------------------------------------------------*/
    public int SetLogLevel(LogLevel newLevel)
    {
        if(newLevel.ordinal() > LogLevel.detail.ordinal())
        {
            LogError( newLevel.toString()
                      + ": Invalid level for logging." );
            return -1;
        }

        LogInform( "setting new logging level to '"
                   + newLevel.toString()
                   + "' from '"
                   + outputLevel.toString()
                   + "'");
        outputLevel = newLevel;
	return 0;

    } /* SetLogLevel() */


    /** ------------------------------------------------------------------------
     * method to change the level at which logging will cause the program to
     * exit.  The only levels that can be set are warning and above.
     *
     * @param failLevel: one of warning, error or fatal
     * @return: 0 on success, -1 on failure
     -------------------------------------------------------------------------*/
    public int SetFailOnLogLevel(LogLevel newLevel)
    {
        if(newLevel.ordinal() > LogLevel.warning.ordinal())
        {
            LogError( newLevel.toString()
                      + ": Invalid fail level for logging.  Must be 'warning', "
                      + "'error', or 'fatal'." );
            return -1;
        }

        LogInform( "Setting new logging failure to "
                   + newLevel.toString()
                   + " from "
                   + failOnLogLevel.toString() );
        failOnLogLevel = newLevel;
        return 0;

    } /* SetFailOnLogLevel() */


    /** ------------------------------------------------------------------------
     * Method callers should use for logging Fatal events, it will also exit
     * using the exit code provided.
     *
     * @param: the message to output
     -------------------------------------------------------------------------*/
    public void LogFatal(String message, int exitCode)
    {
        Log(message, LogLevel.fatal, exitCode);

    } /* LogFatal() */


    public void LogFatal(String message)
    {
        LogFatal(message, 1);
    }


    /** ------------------------------------------------------------------------
     * Method callers should use for logging Error events
     *
     * @param: the message to output
     -------------------------------------------------------------------------*/
    public void LogError(String message)
    {
        Log(message, LogLevel.error);

    } /* LogError() */


    /** ------------------------------------------------------------------------
     * Method callers should use for logging Warning events
     *
     * @param: the message to output
     -------------------------------------------------------------------------*/
    public void LogWarning(String message)
    {
        Log(message, LogLevel.warning);

    } /* LogWarning() */


    /** ------------------------------------------------------------------------
     * Method callers should use for logging Inform events
     *
     * @param: the message to output
     -------------------------------------------------------------------------*/
    public void LogInform(String message)
    {
        Log(message, LogLevel.inform);

    } /* LogInform() */


    /** ------------------------------------------------------------------------
     * Method callers should use for logging Detailed events
     *
     * @param: the message to output
     -------------------------------------------------------------------------*/
    public void LogDetail(String message)
    {
        Log(message, LogLevel.detail);

    } /* LogDetail() */


    /** ------------------------------------------------------------------------
     * Method to validate whether the message should be logged.
     * @param message: the pessage to print
     * @param level: level of the calling method
     -------------------------------------------------------------------------*/
    private void Log(String message, LogLevel level, int exitCode)
    {
        // validate the current level is appropriate for this message
        if(ShouldGenerateOutput(level))
        {
            Output(level.toString() + ": " + message);
        }

        if(IsFailable(level))
            System.exit(exitCode);

    } /* Log() */


    private void Log(String message, LogLevel level)
    {
        Log(message, level, 1);
    } /* Log() */


    /** ------------------------------------------------------------------------
     * Method that will actually generate output. It assumes that the calling
     * method makes sure that the log level calls for output.
     *
     * @param message: the message to output
     -------------------------------------------------------------------------*/
    private void Output(String message)
    {
        System.out.println( exeName + "(" + callingClass + ") " + message );
    } /* Output() */
    
    
    /** ------------------------------------------------------------------------
     * Method that will actually generate output. It assumes that the calling
     * method makes sure that the log level calls for output.
     *
     * @param message: the message to output
     -------------------------------------------------------------------------*/
    private void OutputErr(String message)
    {
        System.err.println( exeName + "(" + callingClass + ") " + message );
    } /* OutputErr() */


    /** ------------------------------------------------------------------------
     * method to test whether the passed in level is less than or equal to the
     * current output level.
     * @param level: the level being tested against current output level.
     * @return: true if the level is >= to current output level false otherwise
     -------------------------------------------------------------------------*/
    private boolean ShouldGenerateOutput(LogLevel level)
    {
        if(level.ordinal() <= outputLevel.ordinal())
            return true;
        else
            return false;

    } /* ShouldGenerateOutput */


    /** ------------------------------------------------------------------------
     * method to return if the input level is less than or equal to the failable
     * level for the system
     * @param level: the level being tested against current failable level.
     * @return true if the current level is failable, false otherwise.
     -------------------------------------------------------------------------*/
    private boolean IsFailable(LogLevel level)
    {
        if(level.ordinal() <= failOnLogLevel.ordinal())
            return true;
        else
            return false;
    } /* isFailable() */

} /* class LogOutput() */
