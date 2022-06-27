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

//Associate a face and its calculated distance
struct IndexDistancePair {
    uint faceIndex; //read index
    float distance;
};

//Workgroup memory.
shared IndexDistancePair local_value[LOCAL_SIZE_X * 2];

//Write to ivec4 vout[]

modelinfo getMInfo() {
    return ol[gl_WorkGroupID.y];
}

//Get vertex index from a model face index
uint getVertexReadIndex(uint faceIndex) {
    if(faceIndex >= getMInfo().size) {
        return DUMMY_INDEX;
    }
    return getMInfo().offset + (faceIndex * 3);
}

uint getUVReadIndex(uint faceIndex) {
    if(faceIndex >= getMInfo().size) {
        return DUMMY_INDEX;
    }
    return getMInfo().uvOffset + (faceIndex * 3);
}

uint getVertexWriteIndex(uint faceIndex) {
    if(faceIndex >= getMInfo().size) {
        return DUMMY_INDEX;
    }
    return getMInfo().idx + (faceIndex * 3);
}

ivec4 vertexIndexToPosition(uint vertexIndex) {
    if (getMInfo().flags < 0) {
         return vb[vertexIndex];
    } else {
        return tempvb[vertexIndex];
    }
}

int getAverageDistance(uint faceIndex) {
    uint vertexIndex = getVertexReadIndex(faceIndex);
    return face_distance(
        vertexIndexToPosition(vertexIndex),
        vertexIndexToPosition(vertexIndex+1),
        vertexIndexToPosition(vertexIndex+2),
        cameraYaw,
        cameraPitch
    );
}

void writeVertexIndexGroup(uint writeFaceIndex, uint readFaceIndex) {
    modelinfo minfo = getMInfo();
    if(readFaceIndex >= minfo.size) {
        return;
    }

    ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);
    uint writeIndex = getVertexWriteIndex(writeFaceIndex);
    uint readIndex = getVertexReadIndex(readFaceIndex);
    uint uvReadIndex = getUVReadIndex(readFaceIndex);
    //TODO UV
    ivec4 thisA, thisB, thisC;
    if (minfo.flags < 0) {
        thisA = vb[readIndex];
        thisB = vb[readIndex+1];
        thisC = vb[readIndex+2];
    } else {
        thisA = tempvb[readIndex];
        thisB = tempvb[readIndex+1];
        thisC = tempvb[readIndex+2];
    }
    vout[writeIndex  ] = thisA + pos;
    vout[writeIndex+1] = thisB + pos;
    vout[writeIndex+2] = thisC + pos;

    if (getMInfo().uvOffset < 0) {
        uvout[writeIndex    ] = vec4(0, 0, 0, 0);
        uvout[writeIndex + 1] = vec4(0, 0, 0, 0);
        uvout[writeIndex + 2] = vec4(0, 0, 0, 0);
    } else if (getMInfo().flags >= 0) {
        uvout[writeIndex    ] = tempuv[readIndex];
        uvout[writeIndex + 1] = tempuv[readIndex+1];
        uvout[writeIndex + 2] = tempuv[readIndex+2];
    } else {
        uvout[writeIndex    ] = uv[readIndex];
        uvout[writeIndex + 1] = uv[readIndex+1];
        uvout[writeIndex + 2] = uv[readIndex+2];
    }
}

void local_main(uint executionType, uint height) {
    uint t = gl_LocalInvocationID.x;
    uint offset = gl_WorkGroupSize.x * 2 * gl_WorkGroupID.x;

    uint faceIndex1 = offset+t*2;
    uint faceIndex2 = offset+t*2+1;
    float distance1 = getAverageDistance(faceIndex1);
    float distance2 = getAverageDistance(faceIndex2);

    //Each local worker must save two elements to local memory,
    //as there are twice as many elements as workers.
    local_value[t*2] = IndexDistancePair(faceIndex1, distance1);
    local_value[t*2+1] = IndexDistancePair(faceIndex2, distance2);

    if (executionType == LOCAL_BMS) {
//        local_bms(height);
    }
    if (executionType == LOCAL_DISPERSE) {
//        local_disperse(height);
    }

    barrier();

    //Write local memory back to buffer
    writeVertexIndexGroup(offset+t*2, local_value[t*2].faceIndex);
    writeVertexIndexGroup(offset+t*2+1, local_value[t*2+1].faceIndex);
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
