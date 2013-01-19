/**
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2007-2012 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.maven.m2settings.usertoken;

import com.google.common.annotations.VisibleForTesting;
import com.sonatype.nexus.usertoken.client.UserToken;
import com.sonatype.nexus.usertoken.plugin.rest.model.AuthTicketXO;
import com.sonatype.nexus.usertoken.plugin.rest.model.UserTokenXO;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.Interpolator;
import org.sonatype.nexus.client.core.NexusClient;
import org.sonatype.nexus.client.rest.UsernamePasswordAuthenticationInfo;
import org.sonatype.nexus.maven.m2settings.TemplateInterpolatorCustomizer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * User-token {@link TemplateInterpolatorCustomizer}.
 *
 * @since 1.4
 */
@Component(role=TemplateInterpolatorCustomizer.class, hint="usertoken", instantiationStrategy="per-lookup")
public class UserTokenTemplateInterpolatorCustomizer
    implements TemplateInterpolatorCustomizer
{
    public static final char SEPARATOR = ':';

    //@NonNls
    public static final String USER_TOKEN = "userToken";

    //@NonNls
    public static final String USER_TOKEN_NAME_CODE = USER_TOKEN + ".nameCode";

    //@NonNls
    public static final String USER_TOKEN_PASS_CODE = USER_TOKEN + ".passCode";

    //@NonNls
    public static final String ENCRYPTED_SUFFIX = ".encrypted";

    @Requirement
    private MasterPasswordEncryption encryption;

    private NexusClient nexusClient;

    // Constructor for Plexus
    public UserTokenTemplateInterpolatorCustomizer() {
        super();
    }

    @VisibleForTesting
    public UserTokenTemplateInterpolatorCustomizer(final MasterPasswordEncryption encryption,
                                                   final NexusClient nexusClient)
    {
        this.encryption = checkNotNull(encryption);
        this.nexusClient = checkNotNull(nexusClient);
    }

    @Override
    public void customize(final NexusClient client, final Interpolator interpolator) {
        this.nexusClient = checkNotNull(client);
        checkNotNull(interpolator);

        interpolator.addValueSource(new AbstractValueSource(false)
        {
            @Override
            public Object getValue(String expression) {
                // Check for encryption flag
                boolean encrypt = false;
                if (expression.toLowerCase().endsWith(ENCRYPTED_SUFFIX)) {
                    encrypt = true;

                    // Strip off suffix and continue
                    expression = expression.substring(0, expression.length() - ENCRYPTED_SUFFIX.length());
                }

                String result = null;
                if (expression.equalsIgnoreCase(USER_TOKEN)) {
                    result = renderUserToken();
                }
                else if (expression.equalsIgnoreCase(USER_TOKEN_NAME_CODE)) {
                    result = getNameCode();
                }
                else if (expression.equalsIgnoreCase(USER_TOKEN_PASS_CODE)) {
                    result = getPassCode();
                }

                // Attempt to encrypt
                if (encrypt && result != null) {
                    try {
                        result = encryption.encrypt(result);
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Failed to encrypt result; Master-password encryption configuration may be missing or invalid", e);
                    }
                }

                return result;
            }
        });
    }

    /**
     * Cached user-token details, as more than one interpolation key may need to use this data.
     *
     * Component using instantiationStrategy="per-lookup" to try and avoid holding on to this for too long.
     */
    private UserTokenXO cachedToken;

    private UserTokenXO getUserToken() {
        if (cachedToken == null) {
            UserToken userToken = nexusClient.getSubsystem(UserToken.class);
            UsernamePasswordAuthenticationInfo auth = (UsernamePasswordAuthenticationInfo) nexusClient.getConnectionInfo().getAuthenticationInfo();
            AuthTicketXO ticket = userToken.authenticate(auth.getUsername(), auth.getPassword());
            cachedToken = userToken.get(ticket.getT());
        }
        return cachedToken;
    }

    public String renderUserToken() {
        return getNameCode() + SEPARATOR + getPassCode();
    }

    public String getNameCode() {
        return getUserToken().getNameCode();
    }

    public String getPassCode() {
        return getUserToken().getPassCode();
    }
}
