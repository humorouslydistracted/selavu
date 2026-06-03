package com.selavu.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["item_id"],
        onDelete = ForeignKey.SET_NULL
    )]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "item_name") val itemName: String,
    @ColumnInfo(name = "item_id", index = true) val itemId: Int? = null,
    val amount: Double,
    val date: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    val notes: String = "",
    @ColumnInfo(name = "include") val include: Boolean = true
)
