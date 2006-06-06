package org.apache.maven.shared.io.location;

import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.shared.io.logging.MessageHolder;

public class ArtifactBasedLocatorStrategy
    implements LocatorStrategy
{
    private final ArtifactFactory factory;

    private final ArtifactResolver resolver;

    private String defaultArtifactType = "jar";

    private final ArtifactRepository localRepository;

    private final List remoteRepositories;

    public ArtifactBasedLocatorStrategy( ArtifactFactory factory, ArtifactResolver resolver,
                                         ArtifactRepository localRepository, List remoteRepositories )
    {
        this.factory = factory;
        this.resolver = resolver;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
    }

    public ArtifactBasedLocatorStrategy( ArtifactFactory factory, ArtifactResolver resolver,
                                         ArtifactRepository localRepository, List remoteRepositories,
                                         String defaultArtifactType )
    {
        this.factory = factory;
        this.resolver = resolver;
        this.localRepository = localRepository;
        this.remoteRepositories = remoteRepositories;
        this.defaultArtifactType = defaultArtifactType;
    }

    public Location resolve( String locationSpecification, MessageHolder messageHolder )
    {
        String[] parts = locationSpecification.split( ":" );

        Location location = null;

        if ( parts.length > 2 )
        {
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[2];

            String type = defaultArtifactType;
            if ( parts.length > 3 )
            {
                type = parts[3];
            }

            String classifier = null;
            if ( parts.length > 4 )
            {
                classifier = parts[4];
            }

            Artifact artifact;
            if ( classifier == null )
            {
                artifact = factory.createArtifact( groupId, artifactId, version, null, type );
            }
            else
            {
                artifact = factory.createArtifactWithClassifier( groupId, artifactId, version, type, classifier );
            }

            messageHolder.append( "Resolving artifact: " + artifact.getId() );
            try
            {
                resolver.resolve( artifact, remoteRepositories, localRepository );
                
                if ( artifact.getFile() != null )
                {
                    location = new ArtifactLocation( artifact, locationSpecification );
                }
            }
            catch ( ArtifactResolutionException e )
            {
                messageHolder.append( e );
            }
            catch ( ArtifactNotFoundException e )
            {
                messageHolder.append( e );
            }
        }

        return location;
    }

}
