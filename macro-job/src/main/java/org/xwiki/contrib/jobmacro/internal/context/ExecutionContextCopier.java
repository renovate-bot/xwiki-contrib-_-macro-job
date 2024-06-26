/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.contrib.jobmacro.internal.context;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.CloneFailedException;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.ExecutionContext;
import org.xwiki.context.ExecutionContextInitializer;
import org.xwiki.context.ExecutionContextManager;
import org.xwiki.velocity.internal.VelocityExecutionContextInitializer;

import com.xpn.xwiki.XWikiContext;

/**
 * Additionally to {@link ExecutionContextManager#clone(ExecutionContext)}, this method also clones the
 * {@link XWikiContext} by using {@link XWikiContextCopier} and thus the resulting {@link ExecutionContext} should be
 * usable in a new thread.
 *
 * @version $Id$
 */
@Component
@Singleton
public class ExecutionContextCopier implements Copier<ExecutionContext>
{
    /** Velocity templates key. */
    private static final String VELOCITY_TEMPLATES = "velocity.templates";

    /** Key used to store the original class loader in the Execution Context. */
    private static final String EXECUTION_CONTEXT_ORIG_CLASSLOADER_KEY = "originalClassLoader";

    /** Key used to store the class loader used by scripts in the Execution Context, see {@link #execution}. */
    private static final String EXECUTION_CONTEXT_CLASSLOADER_KEY = "scriptClassLoader";

    /** Key under which the jar params used for the last macro execution are cached in the Execution Context. */
    private static final String EXECUTION_CONTEXT_JARPARAMS_KEY = "scriptJarParams";

    @Inject
    private ExecutionContextManager executionContextManager;

    @Inject
    private Copier<XWikiContext> xwikiContextCloner;

    @Inject
    @Named("velocity")
    private ExecutionContextInitializer velocityExecutionContextInitializer;

    @Override
    public ExecutionContext copy(ExecutionContext originalExecutionContext) throws CloneFailedException
    {
        try {
            ExecutionContext clonedExecutionContext = this.executionContextManager.clone(originalExecutionContext);

            // XWikiContext
            // The above clone just creates and initializes an empty XWiki Context, so it needs special handling.
            XWikiContext xwikiContext =
                (XWikiContext) originalExecutionContext.getProperty(XWikiContext.EXECUTIONCONTEXT_KEY);
            XWikiContext clonedXWikiContext = xwikiContextCloner.copy(xwikiContext);
            clonedExecutionContext.setProperty(XWikiContext.EXECUTIONCONTEXT_KEY, clonedXWikiContext);

            // VelocityContext
            // Reset the VelocityContext from the EC by removing it and calling the Velocity ECInitializer which is
            // normally called by the execution of the ECInitializers by ECManager.clone(). This ensures a clean new
            // VC is created. It'll get filled when VelocityContextManager.getVelocityContext() is called by whatever
            // code need the VC.
            clonedExecutionContext.removeProperty(VelocityExecutionContextInitializer.VELOCITY_CONTEXT_ID);
            this.velocityExecutionContextInitializer.initialize(clonedExecutionContext);
            // remove velocity.templates also from the execution context, to be re-filled in with the templates from the
            // body of the job macro - it's thread unsafe, if kept it will be manipulated by caller thread and by job
            // thread at the same time
            clonedExecutionContext.removeProperty(VELOCITY_TEMPLATES);

            // clean up the class loaders from the execution context, if any so that 2 threads don't share the same
            // class loaders
            clonedExecutionContext.removeProperty(EXECUTION_CONTEXT_ORIG_CLASSLOADER_KEY);
            clonedExecutionContext.removeProperty(EXECUTION_CONTEXT_CLASSLOADER_KEY);
            clonedExecutionContext.removeProperty(EXECUTION_CONTEXT_JARPARAMS_KEY);

            return clonedExecutionContext;
        } catch (Exception e) {
            throw new CloneFailedException(String.format("Failed to clone [%s]", originalExecutionContext), e);
        }
    }

}

