package org.nnsoft.t2t;

/*
 *    Copyright 2011-2012 The 99 Software Foundation
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import org.nnsoft.t2t.configuration.ConfigurationManager;
import org.nnsoft.t2t.configuration.MigratorConfiguration;
import org.nnsoft.t2t.core.DefaultMigrator;
import org.nnsoft.t2t.core.Migrator;
import org.nnsoft.t2t.core.MigratorException;
import org.openrdf.model.impl.URIImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.FileConverter;

/**
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public class Runner
{

    @Parameter(names = { "-h", "--help" }, description = "Display help information.")
    private boolean printHelp;

    @Parameter(names = { "-v", "--version" }, description = "Display version information.")
    private boolean printVersion;

    @Parameter(
        names = { "-c", "--configuration" },
        description = "Force the use of an alternate XML Configuration file.",
        converter = FileConverter.class
    )
    private File configurationFile = new File(System.getProperty("user.dir"), "t2t-config.xml");

    @Parameter(
        names = { "-e", "--entrypoint" },
        description = "The URL entrypoint.",
        converter = URIImplConverter.class,
        required = true
    )
    private URIImpl entryPoint;

    public void execute( String... args )
    {
        JCommander jCommander = new JCommander( this );
        jCommander.setProgramName( "t2t" );
        jCommander.parseWithoutValidation( args );

        if ( printHelp )
        {
            jCommander.usage();
            System.exit( -1 );
        }

        if ( printVersion )
        {
            Properties properties = new Properties();
            InputStream input =
                Runner.class.getClassLoader().getResourceAsStream( "META-INF/maven/org.99soft/t2t/pom.properties" );

            if ( input != null )
            {
                try
                {
                    properties.load( input );
                }
                catch ( IOException e )
                {
                    // ignore, just don't load the properties
                }
                finally
                {
                    try
                    {
                        input.close();
                    }
                    catch ( IOException e )
                    {
                        // close quietly
                    }
                }
            }

            System.out.printf( "99soft T2T %s (%s)%n",
                               properties.getProperty( "version" ),
                               properties.getProperty( "build" ) );
            System.out.printf( "Java version: %s, vendor: %s%n",
                               System.getProperty( "java.version" ),
                               System.getProperty( "java.vendor" ) );
            System.out.printf( "Java home: %s%n", System.getProperty( "java.home" ) );
            System.out.printf( "Default locale: %s_%s, platform encoding: %s%n",
                               System.getProperty( "user.language" ),
                               System.getProperty( "user.country" ),
                               System.getProperty( "sun.jnu.encoding" ) );
            System.out.printf( "OS name: \"%s\", version: \"%s\", arch: \"%s\", family: \"%s\"%n",
                               System.getProperty( "os.name" ),
                               System.getProperty( "os.version" ),
                               System.getProperty( "os.arch" ),
                               getOsFamily() );

            System.exit( -1 );
        }

        if ( !configurationFile.exists() || configurationFile.isDirectory() )
        {
            System.out.println( String.format( "Non-readable XML Configuration file: %s (No such file).",
                                               configurationFile ) );
            System.exit( -1 );
        }

        if ( entryPoint == null )
        {
            System.out.println( "No URL entrypoint has been specified for this migration." );
            System.exit( -1 );
        }

        System.out.println( "   ____  ____             ______     _________  ______" );
        System.out.println( "  / __ \\/ __ \\_________  / __/ /_   /_  __/__ \\/_  __/" );
        System.out.println( " / /_/ / /_/ / ___/ __ \\/ /_/ __/    / /  __/ / / /" );
        System.out.println( " \\__, /\\__, (__  ) /_/ / __/ /_     / /  / __/ / /" );
        System.out.println( "/____//____/____/\\____/_/  \\__/    /_/  /____//_/\n" );

        // logging stuff
        final Logger logger = LoggerFactory.getLogger( Runner.class );

        // assume SLF4J is bound to logback in the current environment
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        try
        {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext( lc );
            // the context was probably already configured by default configuration
            // rules
            lc.reset();
            configurator.doConfigure( Runner.class.getClassLoader().getResourceAsStream( "logback-config.xml" ) );
        }
        catch ( JoranException je )
        {
            // StatusPrinter should handle this
        }

        logger.info( "Loading configuration from: '{}'", configurationFile );

        MigratorConfiguration configuration = ConfigurationManager.getInstance( configurationFile ).getConfiguration();
        final Migrator migrator = new DefaultMigrator( configuration );

        logger.info( "");
        logger.info( "------------------------------------------------------------------------" );
        logger.info( "T2T MIGRATION migrating RDF graph from '{}' to '{}'",
                     configuration.getSourceGraph(),
                     configuration.getDestinationGraph() );
        logger.info( "------------------------------------------------------------------------" );
        logger.info( "" );

        long start = System.currentTimeMillis();
        int exit = 0;

        try
        {
            migrator.run( entryPoint );
        }
        catch ( MigratorException e )
        {
            logger.error( "An error occurred during the migration process", e );
            exit = 1;
        }
        finally
        {
            logger.info( "");
            logger.info( "------------------------------------------------------------------------" );
            logger.info( "T2T MIGRATION {}", ( exit < 0 ) ? "FAILURE" : "SUCCESS" );
            logger.info( "Total time: {}s", ( ( System.currentTimeMillis() - start ) / 1000 ) );
            logger.info( "Finished at: {}", new Date() );

            final Runtime runtime = Runtime.getRuntime();
            final int megaUnit = 1024 * 1024;
            logger.info( "Final Memory: {}M/{}M",
                         ( runtime.totalMemory() - runtime.freeMemory() ) / megaUnit,
                         runtime.totalMemory() / megaUnit );

            logger.info( "------------------------------------------------------------------------" );

            System.exit( exit );
        }
    }

    private static final String getOsFamily()
    {
        String osName = System.getProperty( "os.name" ).toLowerCase();
        String pathSep = System.getProperty( "path.separator" );

        if ( osName.indexOf( "windows" ) != -1 )
        {
            return "windows";
        }
        else if ( osName.indexOf( "os/2" ) != -1 )
        {
            return "os/2";
        }
        else if ( osName.indexOf( "z/os" ) != -1 || osName.indexOf( "os/390" ) != -1 )
        {
            return "z/os";
        }
        else if ( osName.indexOf( "os/400" ) != -1 )
        {
            return "os/400";
        }
        else if ( pathSep.equals( ";" ) )
        {
            return "dos";
        }
        else if ( osName.indexOf( "mac" ) != -1 )
        {
            if ( osName.endsWith( "x" ) )
            {
                return "mac"; // MACOSX
            }
            return "unix";
        }
        else if ( osName.indexOf( "nonstop_kernel" ) != -1 )
        {
            return "tandem";
        }
        else if ( osName.indexOf( "openvms" ) != -1 )
        {
            return "openvms";
        }
        else if ( pathSep.equals( ":" ) )
        {
            return "unix";
        }

        return "undefined";
    }

    /**
     *
     */
    public static class URIImplConverter
        implements IStringConverter<URIImpl>
    {

        public URIImpl convert( String value )
        {
            return new URIImpl( value );
        }

    }

    public static void main( String[] args )
    {
        new Runner().execute( args );
    }

}
