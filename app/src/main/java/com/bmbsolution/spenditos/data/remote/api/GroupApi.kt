package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface GroupApi {
    @GET("groups")
    suspend fun list(): ApiResponse<List<GroupSummary>>

    @POST("groups")
    suspend fun create(@Body request: GroupCreateRequest): ApiResponse<Group>

    @GET("groups/{id}")
    suspend fun getById(@Path("id") id: String): ApiResponse<Group>

    @PATCH("groups/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: GroupUpdateRequest
    ): ApiResponse<Group>

    @DELETE("groups/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("groups/{id}/invite")
    suspend fun inviteMember(
        @Path("id") groupId: String,
        @Body request: GroupInviteRequest
    ): ApiResponse<Unit>

    @POST("groups/join")
    suspend fun joinGroup(@Body request: JoinGroupRequest): ApiResponse<Group>

    @POST("groups/{id}/leave")
    suspend fun leaveGroup(@Path("id") groupId: String): ApiResponse<Unit>
}
