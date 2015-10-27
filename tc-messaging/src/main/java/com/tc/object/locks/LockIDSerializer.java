/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.object.locks;

import com.tc.io.TCByteBufferInput;
import com.tc.io.TCByteBufferOutput;
import com.tc.io.TCSerializable;
import com.tc.object.locks.LockID.LockIDType;

import java.io.IOException;

public class LockIDSerializer implements TCSerializable<LockIDSerializer> {
  private static final LockIDType[] LOCK_ID_TYPE_VALUES = LockIDType.values();
  
  private LockID lockID;

  public LockIDSerializer() {
    // 
  }

  public LockIDSerializer(LockID lockID) {
    this.lockID = lockID;
  }

  public LockID getLockID() {
    return lockID;
  }

  @Override
  public LockIDSerializer deserializeFrom(TCByteBufferInput serialInput) throws IOException {
    byte type = serialInput.readByte();
    LockID tempLockID = getImpl(type);
    lockID = tempLockID.deserializeFrom(serialInput);
    return this;
  }

  private LockID getImpl(byte type) {
    try {
      switch (LOCK_ID_TYPE_VALUES[type]) {
        case LONG:
          return new LongLockID();
        case STRING:
          return new StringLockID();
        case ENTITY:
          return new EntityLockID();
        default:
          throw new AssertionError("Unknown type : " + type);
      }
    } catch (ArrayIndexOutOfBoundsException e) {
      // stupid javac can't cope with the assertion throw being here...
    }
    throw new AssertionError("Unknown type : " + type);
  }

  @Override
  public void serializeTo(TCByteBufferOutput serialOutput) {
    serialOutput.writeByte((byte) lockID.getLockType().ordinal());
    lockID.serializeTo(serialOutput);
  }
}
