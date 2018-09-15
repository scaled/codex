/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javac.tools.sjavac.comp.dependencies;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaFileObject;

import javac.source.tree.Tree;
import javac.source.util.TaskEvent;
import javac.source.util.TaskListener;
import javac.tools.javac.code.Symbol.ClassSymbol;
import javac.tools.javac.tree.JCTree.JCClassDecl;
import javac.tools.javac.tree.JCTree.JCCompilationUnit;
import javac.tools.javac.util.Context;
import javac.tools.javac.util.DefinedBy;
import javac.tools.javac.util.DefinedBy.Api;
import javac.tools.sjavac.Log;
import javac.tools.sjavac.comp.PubAPIs;
import javac.tools.sjavac.pubapi.PubApi;


public class PublicApiCollector implements TaskListener {

    private Context context;
    private final Set<ClassSymbol> classSymbols = new HashSet<>();
    private final Collection<JavaFileObject> explicitJFOs;

    // Result collected upon compilation task finished
    private Map<String, PubApi> explicitPubApis;
    private Map<String, PubApi> nonExplicitPubApis;

    public PublicApiCollector(Context context,
                              Collection<JavaFileObject> explicitJFOs) {
        this.context = context;
        this.explicitJFOs = explicitJFOs;
    }

    @Override
    @DefinedBy(Api.COMPILER_TREE)
    public void finished(TaskEvent e) {
        switch (e.getKind()) {
        case ANALYZE:
            collectClassSymbols((JCCompilationUnit) e.getCompilationUnit());
            break;
        case COMPILATION:
            Log.debug("Compilation finished");
            Log.debug("Extracting pub APIs for the following symbols:");
            for (ClassSymbol cs : classSymbols)
                Log.debug("    " + cs.fullname);
            extractPubApis();

            // Save result for later retrieval. (Important that we do this
            // before we return from this method, because we may not access
            // symbols after compilation is finished.)
            PubAPIs pa = PubAPIs.instance(context);
            explicitPubApis = pa.getPubapis(explicitJFOs, true);
            nonExplicitPubApis = pa.getPubapis(explicitJFOs, false);

            Log.debug("done");
            break;
        }
    }

    private void collectClassSymbols(JCCompilationUnit cu) {
        for (Tree t : cu.getTypeDecls()) {
            if (t instanceof JCClassDecl)  // Can also be a JCSkip
                classSymbols.add(((JCClassDecl) t).sym);
        }
    }

    private void extractPubApis() {
        // To handle incremental builds (subsequent sjavac invocations) we need
        // to keep track of the public API of what we depend upon.
        //
        // During the recompilation loop (within a single sjavac invocation) we
        // need to keep track of public API of what we're compiling to decide if
        // any dependants needs to be tainted.
        PubAPIs pubApis = PubAPIs.instance(context);
        classSymbols.forEach(pubApis::visitPubapi);
    }

    public Map<String, PubApi> getPubApis(boolean explicit) {
        return explicit ? explicitPubApis : nonExplicitPubApis;
    }
}
