package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.IpBinding

@Entity(tableName = "local_ip_bindings")
data class LocalIpBindingEntity(
    @PrimaryKey
    val macAddress: String,
    val bindingId: String,
    val address: String = "",
    val type: String = "bypassed",
    val comment: String = "",
    val disabled: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
) {
    fun toIpBinding(): IpBinding {
        return IpBinding(
            id = bindingId,
            macAddress = macAddress,
            address = address,
            type = type,
            comment = comment,
            disabled = disabled
        )
    }

    companion object {
        fun fromIpBinding(binding: IpBinding): LocalIpBindingEntity {
            return LocalIpBindingEntity(
                macAddress = binding.macAddress.uppercase(),
                bindingId = binding.id,
                address = binding.address,
                type = binding.type,
                comment = binding.comment,
                disabled = binding.disabled,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        }
    }
}
