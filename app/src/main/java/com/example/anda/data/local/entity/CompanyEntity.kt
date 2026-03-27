package com.example.anda.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "companies",
    indices = [Index(value = ["cnpj"], unique = true)]
)
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cnpj: String,
    val legalName: String,
    val tradeName: String,
    val cnae: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val contactWhatsapp: String = "",
    val preferredContactChannel: String = "app",
    val updatedAt: Long = System.currentTimeMillis()
)

