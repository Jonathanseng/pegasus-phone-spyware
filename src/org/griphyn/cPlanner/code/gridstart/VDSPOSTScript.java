/**
 *  Copyright 2007-2008 University Of Southern California
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.griphyn.cPlanner.code.gridstart;

import edu.isi.pegasus.common.logging.LogManagerFactory;
import org.griphyn.cPlanner.classes.SubInfo;

import org.griphyn.cPlanner.common.PegasusProperties;
import edu.isi.pegasus.common.logging.LogManager;

import org.griphyn.cPlanner.namespace.Dagman;

import org.griphyn.cPlanner.code.POSTScript;

/**
 * An abstract implementation of the interface, that is a superclass for
 * all the VDS supplied postscripts. These postscripts work can parse
 * kickstart records.
 *
 * @author Karan Vahi vahi@isi.edu
 * @version $Revision$
 */

public abstract class VDSPOSTScript implements POSTScript {

    /**
     * The suffix for the exitcode output file, that is generated at the
     * submit host.
     */
    public static final String EXITCODE_OUTPUT_SUFFIX = "exit.log";

    /**
     * The LogManager object which is used to log all the messages.
     */
    protected LogManager mLogger;

    /**
     * The object holding all the properties pertaining to Pegasus.
     */
    protected PegasusProperties mProps;


    /**
     * The path to the exitcode client that parses the exit status of
     * the kickstart. The client is run as a postscript. It also
     * includes the option to the command since at present it is same for all.
     * It is $PEGASUS_HOME/bin/exitcode (no -n)!
     */
    protected String mExitParserPath;

    /**
     * A boolean indicating whether to turn the debug on for the postscript or
     * not.
     */
    protected boolean mPostScriptDebug;

    /**
     * The properties that need to be passed to the postscript invocation
     * on the command line in the java format.
     */
    protected String mPostScriptProperties;

    /**
     * The submit directory where the submit files are being generated for
     * the workflow.
     */
    protected String mSubmitDir;


    /**
     * Returns the path to exitcode that is to be used on the kickstart
     * output.
     *
     * @return the path to the exitcode script to be invoked.
     */
    protected abstract String getDefaultExitCodePath();

    /**
     * The default constructor.
     */
    public VDSPOSTScript(){
        //mLogger = LogManager.getInstance();
    }

    /**
     * Initialize the POSTScript implementation.
     *
     * @param properties the <code>PegasusProperties</code> object containing all
     *                   the properties required by Pegasus.
     * @param path       the path to the POSTScript on the submit host.
     * @param submitDir  the submit directory where the submit file for the job
     *                   has to be generated.
     */
    public void initialize( PegasusProperties properties,
                            String path,
                            String submitDir ){
        mProps     = properties;
        mSubmitDir = submitDir;
        mLogger    = LogManagerFactory.loadSingletonInstance( properties );

        //construct the exitcode paths and arguments
        mExitParserPath       = (path == null ) ? getDefaultExitCodePath() : path;
        mPostScriptDebug      = mProps.setPostSCRIPTDebugON();
        mPostScriptProperties = getPostScriptProperties( properties );

    }

    /**
     * Constructs the postscript that has to be invoked on the submit host
     * after the job has executed on the remote end. The postscript usually
     * works on the xml output generated by kickstart. The postscript invoked
     * is exitcode that is shipped with VDS, and can usually be found at
     * $PEGASUS_HOME/bin/exitcode.
     * <p>
     * The postscript is constructed and populated as a profile
     * in the DAGMAN namespace.
     *
     *
     * @param job  the <code>SubInfo</code> object containing the job description
     *             of the job that has to be enabled on the grid.
     * @param key  the key for the profile that has to be inserted.
     *
     * @return boolean true if postscript was generated,else false.
     */
    public boolean construct( SubInfo job, String key ) {
        String postscript = mExitParserPath;

//        //NO NEED TO REMOVE AS WE ARE HANDLING CORRECTLY IN DAGMAN NAMESPACE
//        //NOW. THERE THE ARGUMENTS AND KEY ARE COMBINED. Karan May 11,2006
//        //arguments are already taken
//        //care of in the profile incorporation
//        postscript += " " +
//                     (String)job.dagmanVariables.removeKey(
//                                              Dagman.POST_SCRIPT_ARGUMENTS_KEY);

        //check if the initialdir condor key has been set
        //for the job or not
//      This is no longer required, as to support
//      submit host execution , all kickstart outputs
//      are propogated back to the submit directory
//      Karan Aug 22, 2005
//        if(job.condorVariables.containsKey("initialdir") &&
//           !job.executionPool.equalsIgnoreCase("local")){
//            String dir = (String)job.condorVariables.get("initialdir");
//            //means that the kickstart output is being
//            //generated in the initialdir instead of the directory
//            //from where the dag is submitted
//            sb.append(dir).append(File.separator);
//        }
//        //append the name of kickstart output
//        sb.append(job.jobName).append(".out");

        //pick up whatever output has been set and set it
        //as a corresponding  DAGMAN profile. Bug Fix for VDS Bug 144
//        postscript += " " + (String)job.condorVariables.get("output");
        job.dagmanVariables.construct( Dagman.OUTPUT_KEY, (String)job.condorVariables.get("output"));


        StringBuffer extraOptions = new StringBuffer();
        if( mPostScriptDebug ){
            //add in the debug options
            appendProperty( extraOptions, "pegasus.log.default", getPostScriptLogFile( job ) );
            appendProperty( extraOptions, "pegasus.verbose", "5" );
        }
        //put in the postscript properties if any
        extraOptions.append( this.mPostScriptProperties );

        //put the extra options into the exitcode arguments
        //in the correct order.
        Object args = job.dagmanVariables.get( Dagman.POST_SCRIPT_ARGUMENTS_KEY );
        StringBuffer arguments = (args == null ) ?
                                     //only have extra options
                                     extraOptions :
                                     //have extra options in addition to existing args
                                     new StringBuffer().append( extraOptions )
                                                       .append( " " ).append( args );
        job.dagmanVariables.construct( Dagman.POST_SCRIPT_ARGUMENTS_KEY, arguments.toString() );


        //put in the postscript
        mLogger.log("Postscript constructed is " + postscript,
                    LogManager.DEBUG_MESSAGE_LEVEL);
        job.dagmanVariables.checkKeyInNS( key, postscript );

        return true;
    }

    /**
     * Returns the path to the postscript log file for a job.
     *
     * @param job  the <code>SubInfo</code> containing job description
     */
    protected String getPostScriptLogFile( SubInfo job ){
        StringBuffer sb = new StringBuffer();
        sb.append( job.getName() ).append( "." ).append( this.EXITCODE_OUTPUT_SUFFIX );
        return sb.toString();
    }

    /**
     * Returns the properties that need to be passed to the the postscript
     * invocation in the java format. It is of the form
     * "-Dprop1=value1 -Dprop2=value2 .."
     *
     * @param properties   the properties object
     *
     * @return the properties list, else empty string.
     */
    protected String getPostScriptProperties( PegasusProperties properties ){
        StringBuffer sb = new StringBuffer();
        appendProperty( sb,
                        "pegasus.user.properties",
                        properties.getPropertiesInSubmitDirectory( ));
        return sb.toString();

    }

    /**
     * Appends a property to the StringBuffer, in the java command line format.
     *
     * @param sb    the StringBuffer to append the property to.
     * @param key   the property.
     * @param value the property value.
     */
    protected void appendProperty( StringBuffer sb, String key, String value ){
        sb.append( " ").append("-D").append( key ).append( "=" ).append( value );
    }


}
