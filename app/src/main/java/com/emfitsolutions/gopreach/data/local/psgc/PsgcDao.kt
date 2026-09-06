package com.emfitsolutions.gopreach.data.local.psgc

import androidx.room.Dao
import androidx.room.Query

/**
 * Read-only lookups over the bundled PSGC reference data (see
 * [PsgcDatabase]'s doc comment). Every search is a `LIKE` against
 * [ProvinceEntity.nameNormalized]/etc. rather than an exact match — the
 * picker (see [com.emfitsolutions.gopreach.ui.components.PhilippineAddressPicker])
 * filters as the publisher types, and the same normalized-contains match is
 * what the reverse-geocode auto-fill (see [com.emfitsolutions.gopreach.data
 * .repository.PhilippineLocationRepository]) uses to resolve Android's
 * Geocoder output back to one of these rows. Results are capped at 50 —
 * plenty for a type-ahead list, and cheap even against the ~42k-row
 * barangay table since every search column is indexed.
 */
@Dao
interface PsgcDao {
    @Query("SELECT * FROM province WHERE nameNormalized LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun searchProvinces(query: String): List<ProvinceEntity>

    @Query("SELECT * FROM province WHERE id = :id")
    suspend fun provinceById(id: Int): ProvinceEntity?

    @Query("SELECT * FROM muncity WHERE provinceId = :provinceId AND nameNormalized LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun searchMuncitiesInProvince(provinceId: Int, query: String): List<MuncityEntity>

    /** Used only when no province has been picked yet — a nationwide search
     * across every city/municipality, still cheap thanks to the name index. */
    @Query("SELECT * FROM muncity WHERE nameNormalized LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun searchMuncitiesNationwide(query: String): List<MuncityEntity>

    @Query("SELECT * FROM muncity WHERE id = :id")
    suspend fun muncityById(id: Int): MuncityEntity?

    @Query("SELECT * FROM barangay WHERE muncityId = :muncityId AND nameNormalized LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    suspend fun searchBarangaysInMuncity(muncityId: Int, query: String): List<BarangayEntity>

    @Query("SELECT * FROM barangay WHERE id = :id")
    suspend fun barangayById(id: Int): BarangayEntity?

    /** Exact-normalized-name match, used by the reverse-geocode auto-fill to
     * resolve Android's Geocoder output to a canonical row before falling
     * back to a looser `LIKE` search. */
    @Query("SELECT * FROM province WHERE nameNormalized = :normalized LIMIT 1")
    suspend fun provinceByExactName(normalized: String): ProvinceEntity?

    @Query("SELECT * FROM muncity WHERE nameNormalized = :normalized AND (:provinceId IS NULL OR provinceId = :provinceId) LIMIT 1")
    suspend fun muncityByExactName(normalized: String, provinceId: Int?): MuncityEntity?

    @Query("SELECT * FROM barangay WHERE nameNormalized = :normalized AND muncityId = :muncityId LIMIT 1")
    suspend fun barangayByExactName(normalized: String, muncityId: Int): BarangayEntity?
}
