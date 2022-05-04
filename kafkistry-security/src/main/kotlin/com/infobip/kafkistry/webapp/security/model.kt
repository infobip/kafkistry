package com.infobip.kafkistry.webapp.security

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.io.Serializable

data class User(
        @JvmField val username: String,
        val firstName: String,
        val lastName: String,
        val email: String,
        val role: UserRole,
        val attributes: Map<String, Serializable> = emptyMap()
) : UserDetails {

    @get:JsonIgnore
    val fullName: String by lazy { "$firstName $lastName" }
    private val allAuthorities = emptyList<GrantedAuthority>().plus(role).plus(role.authorities)

    override fun toString() = "$fullName <$username:$email> $role"

    override fun getUsername(): String = username

    @JsonIgnore
    override fun getAuthorities(): MutableCollection<GrantedAuthority> = allAuthorities.toMutableList()
    @JsonIgnore
    override fun isEnabled(): Boolean = true
    @JsonIgnore
    override fun isCredentialsNonExpired(): Boolean = true
    @JsonIgnore
    override fun getPassword(): String? = null
    @JsonIgnore
    override fun isAccountNonExpired(): Boolean = true
    @JsonIgnore
    override fun isAccountNonLocked(): Boolean = true

}

fun User.isAdmin(): Boolean = role.name == UserRole.ADMIN.name

data class UserAuthority(
        val name: String
) : GrantedAuthority {

    companion object {
        val VIEW_DATA = UserAuthority("VIEW_DATA")
        val REQUEST_CLUSTER_UPDATES = UserAuthority("REQUEST_CLUSTER_UPDATES")
        val REQUEST_TOPIC_UPDATES = UserAuthority("REQUEST_TOPIC_UPDATES")
        val REQUEST_ACL_UPDATES = UserAuthority("REQUEST_ACL_UPDATES")
        val REQUEST_QUOTA_UPDATES = UserAuthority("REQUEST_QUOTA_UPDATES")
        val MANAGE_GIT = UserAuthority("MANAGE_GIT")
        val MANAGE_KAFKA = UserAuthority("MANAGE_KAFKA")
        val MANAGE_CONSUMERS = UserAuthority("MANAGE_CONSUMERS")
        val READ_TOPIC = UserAuthority("READ_TOPIC")
    }

    @JsonIgnore
    override fun getAuthority(): String = name
}

data class UserRole(
        val name: String,
        val authorities: List<UserAuthority>
) : GrantedAuthority {

    companion object {

        fun valueOf(name: String) = when (name) {
            "ADMIN" -> ADMIN
            "USER" -> USER
            "READ_SERVICE" -> READ_SERVICE
            "EMPTY" -> EMPTY
            else -> throw IllegalArgumentException("Unknown role name: '$name'")
        }

        val ADMIN = UserRole("ADMIN",
            UserAuthority.VIEW_DATA,
            UserAuthority.REQUEST_CLUSTER_UPDATES,
            UserAuthority.REQUEST_TOPIC_UPDATES,
            UserAuthority.REQUEST_ACL_UPDATES,
            UserAuthority.REQUEST_QUOTA_UPDATES,
            UserAuthority.MANAGE_GIT,
            UserAuthority.MANAGE_KAFKA,
            UserAuthority.MANAGE_CONSUMERS,
            UserAuthority.READ_TOPIC
        )
        val USER = UserRole("USER",
            UserAuthority.VIEW_DATA,
            UserAuthority.REQUEST_CLUSTER_UPDATES,
            UserAuthority.REQUEST_TOPIC_UPDATES,
            UserAuthority.REQUEST_ACL_UPDATES,
            UserAuthority.REQUEST_QUOTA_UPDATES,
            UserAuthority.MANAGE_CONSUMERS,
            UserAuthority.READ_TOPIC
        )
        val READ_SERVICE = UserRole("READ_SERVICE",
            UserAuthority.VIEW_DATA
        )
        val EMPTY = UserRole("EMPTY")
    }

    fun plusAuthorities(vararg authorities: UserAuthority): UserRole {
        return copy(
                authorities = this.authorities.plus(authorities).distinct()
        )
    }

    constructor(name: String, vararg userAuthorities: UserAuthority) : this(name, listOf(*userAuthorities))

    @JsonIgnore
    override fun getAuthority(): String = "ROLE_$name"  //spring's specification for roles naming

    override fun toString() = "UserRole(name=$name)"

}

