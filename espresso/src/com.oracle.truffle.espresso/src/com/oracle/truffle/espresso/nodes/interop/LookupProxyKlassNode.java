/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.PolyglotInterfaceMappings;

import java.util.HashSet;
import java.util.Set;

@GenerateUncached
public abstract class LookupProxyKlassNode extends EspressoNode {
    static final int LIMIT = 3;

    LookupProxyKlassNode() {
    }

    public abstract ObjectKlass execute(Object metaObject, Klass targetType) throws ClassCastException;

    static String getMetaName(Object metaObject, InteropLibrary interop) {
        assert interop.isMetaObject(metaObject);
        try {
            return interop.asString(interop.getMetaQualifiedName(metaObject));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        }
    }

    static boolean isSame(Object metaObject, String metaQualifiedName, InteropLibrary interop) {
        assert interop.isMetaObject(metaObject);
        return getMetaName(metaObject, interop).equals(metaQualifiedName);
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"isSame(metaObject, metaName, interop)", "targetType == cachedTargetType"}, limit = "LIMIT")
    ObjectKlass doCached(Object metaObject, Klass targetType,
                    @Cached("targetType") Klass cachedTargetType,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                    @Cached("getMetaName(metaObject, interop)") String metaName,
                    @Cached("doUncached(metaObject, targetType, interop)") ObjectKlass cachedProxyKlass) throws ClassCastException {
        assert cachedProxyKlass == doUncached(metaObject, targetType, interop);
        return cachedProxyKlass;
    }

    @TruffleBoundary
    @Specialization(replaces = "doCached")
    ObjectKlass doUncached(Object metaObject, Klass targetType,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary interop) throws ClassCastException {

        assert interop.isMetaObject(metaObject);
        String metaName;
        try {
            metaName = interop.asString(interop.getMetaQualifiedName(metaObject));
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere();
        }
        EspressoForeignProxyGenerator.GeneratedProxyBytes proxyBytes = getContext().getProxyBytesOrNull(metaName);
        if (proxyBytes == null) {
            // cache miss
            Set<ObjectKlass> parentInterfaces = new HashSet<>();
            fillParentInterfaces(metaObject, interop, getContext().getPolyglotInterfaceMappings(), parentInterfaces);
            if (parentInterfaces.isEmpty()) {
                getContext().registerProxyBytes(metaName, null);
                return null;
            }
            proxyBytes = EspressoForeignProxyGenerator.getProxyKlassBytes(metaName, parentInterfaces.toArray(new ObjectKlass[parentInterfaces.size()]), getContext());
        }

        Klass proxyKlass = lookupOrDefineInBindingsLoader(proxyBytes, getContext());

        if (!targetType.isAssignableFrom(proxyKlass)) {
            throw new ClassCastException("proxy object is not instance of expected type: " + targetType.getName());
        }
        return (ObjectKlass) proxyKlass;
    }

    private static Klass lookupOrDefineInBindingsLoader(EspressoForeignProxyGenerator.GeneratedProxyBytes proxyBytes, EspressoContext context) {
        ClassRegistry registry = context.getRegistries().getClassRegistry(context.getBindings().getBindingsLoader());

        Symbol<Symbol.Type> proxyName = context.getTypes().fromClassGetName(proxyBytes.name);
        Klass proxyKlass = registry.findLoadedKlass(proxyName);
        if (proxyKlass == null) {
            proxyKlass = registry.defineKlass(proxyName, proxyBytes.bytes);
        }
        return proxyKlass;
    }

    private static void fillParentInterfaces(Object metaObject, InteropLibrary interop, PolyglotInterfaceMappings mappings, Set<ObjectKlass> parents) throws ClassCastException {
        try {
            if (interop.hasMetaParents(metaObject)) {
                Object metaParents = interop.getMetaParents(metaObject);

                long arraySize = interop.getArraySize(metaParents);
                for (long i = 0; i < arraySize; i++) {
                    Object parent = interop.readArrayElement(metaParents, i);
                    ObjectKlass mappedKlass = mappings.mapName(interop.asString(interop.getMetaQualifiedName(parent)));
                    if (mappedKlass != null) {
                        parents.add(mappedKlass);
                    }
                    fillParentInterfaces(parent, interop, mappings, parents);
                }
            }
        } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
            throw new ClassCastException();
        }
    }
}
