package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Group Models

@Serializable
data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val currency: String,
    val ownerId: String,
    val members: List<GroupMember>,
    val inviteCode: String? = null,
    val settings: GroupSettings? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class GroupMember(
    val userId: String,
    val name: String,
    val picture: String? = null,
    val role: String, // "owner", "admin", "member"
    val joinedAt: String
)

@Serializable
data class GroupSettings(
    val allowMemberInvites: Boolean = true,
    val requireApproval: Boolean = false,
    val defaultSplitType: String = "equal" // "equal", "percentage", "amount"
)

@Serializable
data class GroupCreateRequest(
    val name: String,
    val description: String? = null,
    val currency: String = "USD",
    val icon: String? = null,
    val color: String? = null
)

@Serializable
data class GroupUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val currency: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val settings: GroupSettings? = null
)

@Serializable
data class GroupInviteRequest(
    val email: String? = null,
    val role: String = "member" // "admin", "member"
)

@Serializable
data class JoinGroupRequest(
    val inviteCode: String
)

@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val balance: Double, // User's balance in this group
    val pendingTransactions: Int,
    val icon: String? = null,
    val color: String? = null
)
