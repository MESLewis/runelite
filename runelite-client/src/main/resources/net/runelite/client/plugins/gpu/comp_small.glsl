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
uniform int u_SortHeight = LOCAL_SIZE_X * 2;

shared int totalNum[12]; // number of faces with a given priority
shared int totalDistance[12]; // sum of distances to faces of a given priority

shared int totalMappedNum[18]; // number of faces with a given adjusted priority

shared int min10; // minimum distance to a face of priority 10
shared int dfs[LOCAL_SIZE_X]; // packed face id and distance
shared int priority[LOCAL_SIZE_X]; //priority

#include priority_render.glsl

//Associate a face and its calculated distance
struct IndexDistancePair {
    uint faceIndex; //read index
    int distance;
    int priority;
};

//Workgroup memory.
shared IndexDistancePair local_value[LOCAL_SIZE_X];

//Write to ivec4 vout[]

modelinfo getMInfo() {
    return ol[gl_WorkGroupID.x];
}

//Get vertex index from a model face index
uint getVertexReadIndex(uint faceIndex) {
//    if(faceIndex >= getMInfo().size) {
//        return DUMMY_INDEX;
//    }
    return getMInfo().offset + (faceIndex * 3);
}

uint getUVReadIndex(uint faceIndex) {
//    if(faceIndex >= getMInfo().size) {
//        return DUMMY_INDEX;
//    }
    return getMInfo().uvOffset + (faceIndex * 3);
}

uint getVertexWriteIndex(uint faceIndex) {
//    if(faceIndex >= getMInfo().size) {
//        return DUMMY_INDEX;
//    }
    return getMInfo().idx + (faceIndex * 3);
}

ivec4 vertexIndexToPosition(uint vertexIndex) {
    ivec4 pos;
    if (getMInfo().flags < 0) {
         pos = vb[vertexIndex];
    } else {
        pos = tempvb[vertexIndex];
    }
    int orientation = getMInfo().flags & 0x7ff;
    return rotate(pos, orientation);
}

int getAverageDistance(uint faceIndex) {
    if(faceIndex == DUMMY_INDEX) {
        return DUMMY_DISTANCE;
    }
    uint vertexIndex = getVertexReadIndex(faceIndex);
    ivec4 thisA = vertexIndexToPosition(vertexIndex);
    ivec4 thisB = vertexIndexToPosition(vertexIndex+1);
    ivec4 thisC = vertexIndexToPosition(vertexIndex+2);
    int radius = (getMInfo().flags & 0x7fffffff) >> 12;
    int thisPriority = (thisA.w >> 16) & 0xff;
    return radius + face_distance(
        thisA,
        thisB,
        thisC,
        cameraYaw,
        cameraPitch
    );
//    return dfs[faceIndex];
}

void writeVertexIndexGroup(uint writeFaceIndex, uint readFaceIndex) {
    modelinfo minfo = getMInfo();
    if(readFaceIndex >= minfo.size || writeFaceIndex >= minfo.size) {
        return;
    }

    ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);
    uint writeIndex = getVertexWriteIndex(writeFaceIndex);
    uint readIndex = getVertexReadIndex(readFaceIndex);
    uint uvReadIndex = getUVReadIndex(readFaceIndex);

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

    int orientation = minfo.flags & 0x7ff;
    ivec4 thisrvA = rotate(thisA, orientation);
    ivec4 thisrvB = rotate(thisB, orientation);
    ivec4 thisrvC = rotate(thisC, orientation);


    vout[writeIndex  ] = thisrvA + pos;
    vout[writeIndex+1] = thisrvB + pos;
    vout[writeIndex+2] = thisrvC + pos;

    //TODO DEBUG
//    IndexDistancePair d = local_value[gl_LocalInvocationID.x];
//    int id1 = d.distance >> 16;
//    int distance1 = d.distance & 0xffff;
//    vout[writeIndex  ] = ivec4(readFaceIndex, writeFaceIndex, readIndex, writeIndex);
//    vout[writeIndex+1] = ivec4(minfo.size,minfo.offset,minfo.idx,1);
//    vout[writeIndex+2] = ivec4(2,d.priority,id1,distance1);
//    return;

    if (getMInfo().uvOffset < 0) {
        uvout[writeIndex    ] = vec4(0, 0, 0, 0);
        uvout[writeIndex + 1] = vec4(0, 0, 0, 0);
        uvout[writeIndex + 2] = vec4(0, 0, 0, 0);
    } else if (getMInfo().flags >= 0) {
        uvout[writeIndex    ] = tempuv[uvReadIndex];
        uvout[writeIndex + 1] = tempuv[uvReadIndex+1];
        uvout[writeIndex + 2] = tempuv[uvReadIndex+2];
    } else {
        uvout[writeIndex    ] = uv[uvReadIndex];
        uvout[writeIndex + 1] = uv[uvReadIndex+1];
        uvout[writeIndex + 2] = uv[uvReadIndex+2];
    }
}

//Compare and swap elements in workgroup-local memory
void local_compare_and_swap(uvec2 idx) {
    if(idx.x >= getMInfo().size || idx.y >= getMInfo().size) {
        return;
    }
    int d1 = local_value[idx.x].distance;
    int id1 = d1 >> 16;
    int distance1 = d1 & 0xffff;
    int priority1 = local_value[idx.x].priority;
    int d2 = local_value[idx.y].distance;
    int id2 = d2 >> 16;
    int distance2 = d2 & 0xffff;
    int priority2 = local_value[idx.y].priority;

    int shouldFlip = 0;

    if(priority1 > priority2) {
        shouldFlip = 1;
    }
    if(priority1 == priority2) {
        if(distance1 < distance2) {
            shouldFlip = 1;
        }
        if(distance1 == distance2) {
            if(id1 >= id2) {
                shouldFlip = 1;
            }
        }
    }

    if(shouldFlip == 1) {
        IndexDistancePair tmp = local_value[idx.x];
        local_value[idx.x] = local_value[idx.y];
        local_value[idx.y] = tmp;
    }
}

void local_flip(uint h) {
    uint t = gl_LocalInvocationID.x;
    barrier();

    uint half_h = h >> 1;
    ivec2 indices =
    ivec2( h * ( ( 2 * t ) / h ) ) +
    ivec2( t % half_h, h - 1 - ( t % half_h ) );

    local_compare_and_swap(indices);
}

void local_disperse(in uint h){
    uint t = gl_LocalInvocationID.x;
    for(; h > 1; h /= 2) {
        barrier();
        uint half_h = h >> 1;
        ivec2 indices =
        ivec2( h * ( ( 2 * t ) / h ) ) +
        ivec2( t % half_h, half_h + ( t % half_h ) );

        local_compare_and_swap(indices);
    }
}

void local_bms(uint h) {
    for (uint hh = 2; hh <= h; hh <<= 1) {
        local_flip(hh);
        local_disperse(hh/2);
    }
}


void local_main(uint executionType, uint height) {
    uint t = gl_LocalInvocationID.x;
//    uint offset = gl_WorkGroupSize.x * 2 * gl_WorkGroupID.x;
    uint offset = 0;

    uint faceIndex1 = offset+t*2;
    uint faceIndex2 = offset+t*2+1;
    int distance1 = getAverageDistance(faceIndex1);
    int distance2 = getAverageDistance(faceIndex2);
    IndexDistancePair idp1;
    IndexDistancePair idp2;

    if(faceIndex1 <= getMInfo().size) {
        idp1 = IndexDistancePair(faceIndex1, distance1, priority[faceIndex1]);
    } else {
        faceIndex1 = DUMMY_INDEX;
        idp1 = IndexDistancePair(DUMMY_INDEX, DUMMY_DISTANCE, 0);
    }
    if(faceIndex2 <= getMInfo().size) {
        idp2 = IndexDistancePair(faceIndex2, distance2, priority[faceIndex2]);
    } else {
        faceIndex1 = DUMMY_INDEX;
        idp2 = IndexDistancePair(DUMMY_INDEX, DUMMY_DISTANCE, 0);
    }

    //Each local worker must save two elements to local memory,
    //as there are twice as many elements as workers.
    local_value[t*2] = idp1;
    local_value[t*2+1] = idp2;

//    if (executionType == LOCAL_BMS) {
        local_bms(height);
//    }
//    if (executionType == LOCAL_DISPERSE) {
//        local_disperse(height);
//    }
//    uint one = t;
//    uint two = t + LOCAL_SIZE_X / 2;
//    IndexDistancePair tmp = local_value[one];
//    local_value[one] = local_value[two];
//    local_value[two] = tmp;


    memoryBarrierShared();
    barrier();

    //Write local memory back to buffer
    writeVertexIndexGroup(faceIndex1, local_value[t*2].faceIndex);
    writeVertexIndexGroup(faceIndex2, local_value[t*2+1].faceIndex);
}

void main() {
    //    uint height = gl_WorkGroupSize.x * 2;
    //    uint groupId = gl_WorkGroupID.x;//Model number, compare to minfo.size
    //    uint localId = gl_LocalInvocationID.x;
    //    modelinfo minfo = getMInfo();
    //    uint indexLength = minfo.size * 3;//Face count * index entries per face
    //    uint computeSize = uint(pow(2, ceil(log(indexLength)/log(2))));
    //    uint usedWorkgroups = (computeSize / (gl_WorkGroupSize.x * 2)) + 1;
    //
    //    if (gl_WorkGroupID.x >= usedWorkgroups) {
    //        return;
    //    }
    //
    //    //TODO GLOBAL
    //    local_main(LOCAL_BMS, u_SortHeight);

//}

//void main() {
    uint groupId = gl_WorkGroupID.x;
    uint localId = gl_LocalInvocationID.x;
    modelinfo minfo = ol[groupId];
    ivec4 pos = ivec4(minfo.x, minfo.y, minfo.z, 0);

    if (localId == 0) {
        min10 = 1600;
        for (int i = 0; i < 12; ++i) {
            totalNum[i] = 0;
            totalDistance[i] = 0;
        }
        for (int i = 0; i < 18; ++i) {
            totalMappedNum[i] = 0;
        }
    }

    int prio1, dis1;
    ivec4 vA1, vA2, vA3;

    get_face(localId, minfo, cameraYaw, cameraPitch, prio1, dis1, vA1, vA2, vA3);

    memoryBarrierShared();
    barrier();

    add_face_prio_distance(localId, minfo, vA1, vA2, vA3, prio1, dis1, pos);

    memoryBarrierShared();
    barrier();

    int prio1Adj;
    int idx1 = map_face_priority(localId, minfo, prio1, dis1, prio1Adj);
    priority[localId] = prio1Adj;

    memoryBarrierShared();
    barrier();

    insert_dfs(localId, minfo, prio1Adj, dis1, idx1);

    memoryBarrierShared();
    barrier();

//    sort_and_insert(localId, minfo, prio1Adj, dis1, vA1, vA2, vA3);
    //Local main grabs 2 indices at a time, so only run half of them.
    //TODO DEBUG
    if(gl_LocalInvocationID.x < LOCAL_SIZE_X/2) {
        local_main(LOCAL_BMS, LOCAL_SIZE_X);
    }
}
