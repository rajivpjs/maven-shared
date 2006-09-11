package org.apache.maven.user.acegi;

/*
 * Copyright 2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.acegisecurity.acl.basic.AclObjectIdentity;
import org.acegisecurity.acl.basic.BasicAclEntry;
import org.acegisecurity.acl.basic.BasicAclExtendedDao;
import org.acegisecurity.acl.basic.NamedEntityObjectIdentity;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.maven.user.acegi.acl.basic.ExtendedSimpleAclEntry;
import org.apache.maven.user.model.InstancePermissions;
import org.apache.maven.user.model.User;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.springframework.beans.factory.InitializingBean;

/**
 * Utility class to handle ACLs.
 * 
 * @plexus.component role="org.apache.maven.user.acegi.AclManager"
 * 
 * @author <a href="mailto:carlos@apache.org">Carlos Sanchez</a>
 * @version $Id$
 */
public class AclManager
    implements Initializable
{
    public static final String ROLE = AclManager.class.getName();

    private BasicAclExtendedDao aclDao;

    public void setAclDao( BasicAclExtendedDao aclDao )
    {
        this.aclDao = aclDao;
    }

    public BasicAclExtendedDao getAclDao()
    {
        return aclDao;
    }

    protected String getCurrentUserName()
    {
        return ( (org.acegisecurity.userdetails.User) SecurityContextHolder.getContext().getAuthentication()
            .getPrincipal() ).getUsername();
    }

    protected void delete( Class clazz, Object id )
    {
        getAclDao().delete( createObjectIdentity( clazz, id ) );
    }

    protected NamedEntityObjectIdentity createObjectIdentity( Class clazz, Object id )
    {
        if ( ( clazz == null ) || ( id == null ) )
        {
            return null;
        }
        return new NamedEntityObjectIdentity( clazz.getName(), id.toString() );
    }

    private BasicAclEntry[] getAcls( Class clazz, Object id )
    {
        NamedEntityObjectIdentity objectIdentity = createObjectIdentity( clazz, id );
        BasicAclEntry[] acls = getAclDao().getAcls( objectIdentity );
        return acls;
    }

    private BasicAclEntry getAcl( Class clazz, Object id, String userName )
    {
        BasicAclEntry[] acls = getAcls( clazz, id );
        if ( acls != null )
        {
            /* TODO optimize this, probably the results come ordered in some way */
            for ( int i = 0; i < acls.length; i++ )
            {
                if ( acls[i].getRecipient().equals( userName ) )
                {
                    return acls[i];
                }
            }
        }
        return null;
    }

    /**
     * Get the instance permissions for each user and object ( identified by its class and id )
     * 
     * @param clazz {@link Class} of the object
     * @param id identifier of the object
     * @param userPermissions {@link List} &lt; {@link InstancePermissions} > with one permission object
     * for each user we want to retrieve permissions. Permissions in that object will be overwritten.  
     * @return {@link List} &lt; {@link InstancePermissions} >
     */
    public List getUsersInstancePermissions( Class clazz, Object id, List userPermissions )
    {
        BasicAclEntry[] acls = getAcls( clazz, id );

        /* put ACLs in a map indexed by username */
        Map aclsByUserName = new HashMap();
        for ( int i = 0; i < acls.length; i++ )
        {
            BasicAclEntry acl = acls[i];
            String recipient = (String) acl.getRecipient();

            BasicAclEntry p = (BasicAclEntry) aclsByUserName.get( recipient );
            if ( p != null )
            {
                throw new IllegalStateException( "There is more than one ACL for user '" + recipient + "': " + p
                    + " and " + acl );
            }

            aclsByUserName.put( recipient, acl );
        }

        /* add permissions to each user, and then return a List with permissions */
        Iterator it = userPermissions.iterator();
        while ( it.hasNext() )
        {
            InstancePermissions p = (InstancePermissions) it.next();
            BasicAclEntry acl = (BasicAclEntry) aclsByUserName.get( p.getUser().getUsername() );
            if ( acl != null )
            {
                aclToPermission( acl, p );
            }
        }
        return userPermissions;
    }

    /**
     * Updates a list of permissions at the same time. If the permission didn't exist it's created.
     * 
     * @param permissions {@link Collection} &lt;{@link InstancePermissions}> .
     * Each {@link InstancePermissions}.user only needs to have username, no other properties are required.
     */
    public void setUsersInstancePermissions( Collection permissions )
    {
        Iterator it = permissions.iterator();
        while ( it.hasNext() )
        {
            InstancePermissions permission = (InstancePermissions) it.next();
            setUsersInstancePermission( permission );
        }
    }

    /**
     * Updates a permission. If the permission didn't exist it's created.
     * 
     * @param permission {@link InstancePermissions} .
     * Each {@link InstancePermissions}.user only needs to have username, no other properties are required.
     */
    public void setUsersInstancePermission( InstancePermissions permission )
    {

        User user = permission.getUser();

        String userName = null;
        if ( user != null )
        {
            userName = user.getUsername();
        }

        BasicAclEntry acl = getAcl( permission.getInstanceClass(), permission.getId(), userName );

        if ( acl == null )
        {
            acl = new ExtendedSimpleAclEntry();
            permissionToAcl( permission, acl );

            /* create the ACL only if it has any permission */
            if ( ( userName == null ) || ( acl.getMask() != ExtendedSimpleAclEntry.NOTHING ) )
            {
                getAclDao().create( acl );
            }
        }
        else
        {
            permissionToAcl( permission, acl );

            /* delete the ACL if it has no permissions */
            if ( acl.getMask() != ExtendedSimpleAclEntry.NOTHING )
            {
                getAclDao().changeMask( acl.getAclObjectIdentity(), userName, new Integer( acl.getMask() ) );
            }
            else
            {
                getAclDao().delete( acl.getAclObjectIdentity(), userName );
            }
        }
    }

    private void permissionToAcl( InstancePermissions p, BasicAclEntry basicAcl )
    {
        if ( !( basicAcl instanceof ExtendedSimpleAclEntry ) )
        {
            throw new IllegalArgumentException( "Can't create ACLs other than " + ExtendedSimpleAclEntry.class );
        }

        ExtendedSimpleAclEntry acl = (ExtendedSimpleAclEntry) basicAcl;

        User user = p.getUser();

        if ( user != null )
        {
            acl.setRecipient( user.getUsername() );
        }

        acl.setAclObjectIdentity( createObjectIdentity( p.getInstanceClass(), p.getId() ) );
        acl.setAclObjectParentIdentity( createObjectIdentity( p.getParentClass(), p.getParentId() ) );
        acl.setMask( ExtendedSimpleAclEntry.NOTHING );

        if ( p.isExecute() )
        {
            acl.addPermission( ExtendedSimpleAclEntry.CREATE );
        }
        if ( p.isDelete() )
        {
            acl.addPermission( ExtendedSimpleAclEntry.DELETE );
        }
        if ( p.isRead() )
        {
            acl.addPermission( ExtendedSimpleAclEntry.READ );
        }
        if ( p.isWrite() )
        {
            acl.addPermission( ExtendedSimpleAclEntry.WRITE );
        }
        if ( p.isAdminister() )
        {
            acl.addPermission( ExtendedSimpleAclEntry.ADMINISTRATION );
        }
    }

    /**
     * This method translates Acegi {@link BasicAclEntry} to Maven {@link InstancePermissions}.
     * 
     * @param acl Permissions in Acegi world
     * @param p Permissions in Maven world
     */
    private void aclToPermission( BasicAclEntry acl, InstancePermissions p )
    {
        AclObjectIdentity aclObjectIdentity = acl.getAclObjectIdentity();
        AclObjectIdentity aclObjectParentIdentity = acl.getAclObjectParentIdentity();

        if ( !( aclObjectIdentity instanceof NamedEntityObjectIdentity ) )
        {
            throw new IllegalArgumentException( "aclObjectIdentity is instance of "
                + aclObjectIdentity.getClass().getName() + " and only " + NamedEntityObjectIdentity.class.getName()
                + " is allowed." );
        }
        if ( !( aclObjectParentIdentity instanceof NamedEntityObjectIdentity ) )
        {
            throw new IllegalArgumentException( "aclObjectParentIdentity is instance of "
                + aclObjectParentIdentity.getClass().getName() + " and only "
                + NamedEntityObjectIdentity.class.getName() + " is allowed." );
        }

        try
        {
            NamedEntityObjectIdentity aclId = (NamedEntityObjectIdentity) aclObjectIdentity;
            p.setInstanceClass( Class.forName( aclId.getClassname() ) );
            p.setId( aclId.getId() );

            if ( aclObjectParentIdentity != null )
            {
                NamedEntityObjectIdentity aclParentId = (NamedEntityObjectIdentity) aclObjectParentIdentity;
                p.setParentClass( Class.forName( aclParentId.getClassname() ) );
                p.setParentId( aclParentId.getId() );
            }
        }
        catch ( ClassNotFoundException e )
        {
            throw new RuntimeException( e );
        }

        if ( acl.isPermitted( ExtendedSimpleAclEntry.CREATE ) )
        {
            p.setExecute( true );
        }
        if ( acl.isPermitted( ExtendedSimpleAclEntry.DELETE ) )
        {
            p.setDelete( true );
        }
        if ( acl.isPermitted( ExtendedSimpleAclEntry.READ ) )
        {
            p.setRead( true );
        }
        if ( acl.isPermitted( ExtendedSimpleAclEntry.WRITE ) )
        {
            p.setWrite( true );
        }
        if ( acl.isPermitted( ExtendedSimpleAclEntry.ADMINISTRATION ) )
        {
            p.setAdminister( true );
        }
    }

    /**
     * Initializes DAO using Spring {@link InitializingBean#afterPropertiesSet()}.
     */
    public void initialize()
        throws InitializationException
    {
        /* execute Spring initialization callback */
        if ( getAclDao() instanceof InitializingBean )
        {
            InitializingBean initializingBean = (InitializingBean) getAclDao();
            try
            {
                initializingBean.afterPropertiesSet();
            }
            catch ( Exception e )
            {
                throw new InitializationException( "Unable to initialize ACL DAO", e );
            }
        }
    }
}
