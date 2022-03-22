/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * LASTORE bytecode with interop extensions.
 *
 * <p>
 * Augmented with two interop extensions:
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, LASTORE is
 * mapped to {@link InteropLibrary#writeArrayElement(Object, long, Object)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the underlying interop array does not accept
 * writing a long.</li>
 * <li>Throws guest {@link ArrayStoreException} if the underlying interop array is read-only.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "LASTORE")
public abstract class LongArrayStore extends Node {

    public abstract void execute(StaticObject receiver, int index, long value);

    @Specialization
    void executeWithNullCheck(StaticObject array, int index, long value,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck longArrayStore) {
        longArrayStore.execute(nullCheck.execute(array), index, value);
    }

    @GenerateUncached
    @NodeInfo(shortName = "LASTORE !nullcheck")
    public abstract static class WithoutNullCheck extends Node {
        static final int LIMIT = 2;

        public abstract void execute(StaticObject receiver, int index, long value);

        protected final EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        protected final EspressoLanguage getLanguage() {
            return EspressoLanguage.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        void doEspresso(StaticObject array, int index, long value) {
            assert !StaticObject.isNull(array);
            getContext().getInterpreterToVM().setArrayLong(value, index, array);
        }

        @Specialization(guards = "array.isForeignObject()")
        void doArrayLike(StaticObject array, int index, long value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            ForeignArrayUtils.writeForeignArrayElement(array, index, value, language, getContext().getMeta(), interop, exceptionProfile);
        }
    }
}
