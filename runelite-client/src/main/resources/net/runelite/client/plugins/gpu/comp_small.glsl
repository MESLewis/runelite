/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include version_header
#define LOCAL_SIZE_X 1024
#define LOCAL_BMS 0
#define LOCAL_DISPERSE 1
#define GLOBAL_FLIP 2
#define GLOBAL_DISPERSE 3
#define DUMMY_INDEX 10000000
#define DUMMY_DISTANCE -1000000
#include comp_common.glsl
#include common.glsl

layout(local_size_x = LOCAL_SIZE_X) in;

uniform int u_ExecutionType;
uniform int u_SortHeight = 2048;

//Pair of 3 indicies that index into vb[]
//full indexes
struct IndexGroup {
    uint i1;
    uint i2;
    uint i3;
};

//Associate a face and its calculated distance
struct IndexDistancePair {
    IndexGroup indexGroup;
    float distance;
};

//Workgroup memory.
shared IndexDistancePair local_value[LOCAL_SIZE_X * 2];

//Write to ivec4 vout[]

modelinfo getMInfo() {
    return ol[gl_WorkGroupID.y];
}

uint getFullIndex(uint localIndex) {
    if(localIndex * 3 >= getMInfo().size) {
        return DUMMY_INDEX;
    }
    return getMInfo().offset + (localIndex * 3);
}

IndexGroup readIndexGroup(uint localIndex) {
    uint baseIndex = getMInfo().offset + localIndex;
    return IndexGroup(baseIndex, baseIndex+1, baseIndex+2);
}

ivec4 indexToPosition(uint fullIndex) {
    if (getMInfo().flags < 0) {
         return vb[fullIndex];
    } else {
        return tempvb[fullIndex];
    }
}

int getAverageDistance(IndexGroup group) {
    return face_distance(
        indexToPosition(group.i1),
        indexToPosition(group.i2),
        indexToPosition(group.i3),
        cameraYaw,
        cameraPitch
    );
}

void writeIndexGroup(uint localIndex, IndexGroup indexGroup) {
    modelinfo minfo = getMInfo();
    ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);
    uint writeIndex = minfo.idx + localIndex;
    //TODO UV
    ivec4 thisA, thisB, thisC;
    if (minfo.flags < 0) {
        thisA = vb[indexGroup.i1];
        thisB = vb[indexGroup.i2];
        thisC = vb[indexGroup.i3];
    } else {
        thisA = tempvb[indexGroup.i1];
        thisB = tempvb[indexGroup.i2];
        thisC = tempvb[indexGroup.i3];
    }
    vout[writeIndex  ] = thisA + pos;
    vout[writeIndex+1] = thisB + pos;
    vout[writeIndex+2] = thisC + pos;

    if (getMInfo().uvOffset < 0) {
        uvout[writeIndex    ] = vec4(0, 0, 0, 0);
        uvout[writeIndex + 1] = vec4(0, 0, 0, 0);
        uvout[writeIndex + 2] = vec4(0, 0, 0, 0);
    } else if (getMInfo().flags >= 0) {
        uvout[writeIndex    ] = tempuv[indexGroup.i1];
        uvout[writeIndex + 1] = tempuv[indexGroup.i1 + 1];
        uvout[writeIndex + 2] = tempuv[indexGroup.i1 + 2];
    } else {
        uvout[writeIndex    ] = uv[indexGroup.i1];
        uvout[writeIndex + 1] = uv[indexGroup.i1 + 1];
        uvout[writeIndex + 2] = uv[indexGroup.i1 + 2];
    }
}

void local_main(uint executionType, uint height) {
    uint t = gl_LocalInvocationID.x;
    uint offset = gl_WorkGroupSize.x * 2 * gl_WorkGroupID.x;

    uint fullIndex1 = getFullIndex(offset+t*2);
    uint fullIndex2 = getFullIndex(offset+t*2+1);
    IndexGroup rig1 = readIndexGroup(fullIndex1);
    IndexGroup rig2 = readIndexGroup(fullIndex2);
    float distance1 = getAverageDistance(rig1);
    float distance2 = getAverageDistance(rig2);

    if (fullIndex1 == DUMMY_INDEX) {
        rig1 = IndexGroup(DUMMY_INDEX, DUMMY_INDEX, DUMMY_INDEX);
        distance1 = DUMMY_DISTANCE;
    }
    if (fullIndex2 == DUMMY_INDEX) {
        rig2 = IndexGroup(DUMMY_INDEX, DUMMY_INDEX, DUMMY_INDEX);
        distance2 = DUMMY_DISTANCE;
    }

    //Each local worker must save two elements to local memory,
    //as there are twice as many elements as workers.
    local_value[t*2] = IndexDistancePair(rig1, distance1);
    local_value[t*2+1] = IndexDistancePair(rig2, distance2);

    if (executionType == LOCAL_BMS) {
//        local_bms(height);
    }
    if (executionType == LOCAL_DISPERSE) {
//        local_disperse(height);
    }

    barrier();

    //Write local memory back to buffer
    IndexGroup ig1 = local_value[t*2].indexGroup;
    IndexGroup ig2 = local_value[t*2+1].indexGroup;

    if (fullIndex1 != DUMMY_INDEX) {
        writeIndexGroup(fullIndex1, ig1);
    }
    if (fullIndex2 != DUMMY_INDEX) {
        writeIndexGroup(fullIndex2, ig2);
    }
}

void main() {
    uint height = gl_WorkGroupSize.x * 2;
    uint groupId = gl_WorkGroupID.x;//Model number, compare to minfo.size
    uint localId = gl_LocalInvocationID.x;
    modelinfo minfo = getMInfo();
    uint indexLength = minfo.size * 3;//Face count * index entries per face
    uint computeSize = uint(pow(2, ceil(log(indexLength)/log(2))));
    uint usedWorkgroups = (computeSize / (gl_WorkGroupSize.x * 2)) + 1;

    if (gl_WorkGroupID.x >= usedWorkgroups) {
        return;
    }

    //TODO GLOBAL
    local_main(LOCAL_BMS, u_SortHeight);


    /*
    int offset = minfo.offset;
    int size = minfo.size;
    int outOffset = minfo.idx;
    int uvOffset = minfo.uvOffset;
    int flags = minfo.flags;
    ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);

    uint ssboOffset = localId;
    ivec4 thisA, thisB, thisC;

    // Grab triangle vertices from the correct buffer
    if (flags < 0) {
        thisA = vb[offset + ssboOffset * 3];
        thisB = vb[offset + ssboOffset * 3 + 1];
        thisC = vb[offset + ssboOffset * 3 + 2];
    } else {
        thisA = tempvb[offset + ssboOffset * 3];
        thisB = tempvb[offset + ssboOffset * 3 + 1];
        thisC = tempvb[offset + ssboOffset * 3 + 2];
    }

    uint myOffset = localId;

    // position vertices in scene and write to out buffer
    vout[outOffset + myOffset * 3]     = pos + thisA;
    vout[outOffset + myOffset * 3 + 1] = pos + thisB;
    vout[outOffset + myOffset * 3 + 2] = pos + thisC;

    if (uvOffset < 0) {
        uvout[outOffset + myOffset * 3]     = vec4(0, 0, 0, 0);
        uvout[outOffset + myOffset * 3 + 1] = vec4(0, 0, 0, 0);
        uvout[outOffset + myOffset * 3 + 2] = vec4(0, 0, 0, 0);
    } else if (flags >= 0) {
        uvout[outOffset + myOffset * 3]     = tempuv[uvOffset + localId * 3];
        uvout[outOffset + myOffset * 3 + 1] = tempuv[uvOffset + localId * 3 + 1];
        uvout[outOffset + myOffset * 3 + 2] = tempuv[uvOffset + localId * 3 + 2];
    } else {
        uvout[outOffset + myOffset * 3]     = uv[uvOffset + localId * 3];
        uvout[outOffset + myOffset * 3 + 1] = uv[uvOffset + localId * 3 + 1];
        uvout[outOffset + myOffset * 3 + 2] = uv[uvOffset + localId * 3 + 2];
    }
    */
}
