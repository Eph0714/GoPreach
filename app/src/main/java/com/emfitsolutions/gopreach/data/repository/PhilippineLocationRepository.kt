package com.emfitsolutions.gopreach.data.repository

import com.emfitsolutions.gopreach.data.local.psgc.BarangayEntity
import com.emfitsolutions.gopreach.data.local.psgc.MuncityEntity
import com.emfitsolutions.gopreach.data.local.psgc.PsgcDao
import com.emfitsolutions.gopreach.data.local.psgc.ProvinceEntity
import com.emfitsolutions.gopreach.data.location.GeocodedAddress
import javax.inject.Inject
import javax.inject.Singleton

/** One resolved level of [PhilippineAddressSelection] — [id] is the PSGC
 * row's own id (used to query the next level down), [name] what's shown. */
data class PsgcOption(val id: Int, val name: String)

/** What [PhilippineAddressPicker][com.emfitsolutions.gopreach.ui.components
 * .PhilippineAddressPicker] holds and what a caller ultimately saves onto
 * [com.emfitsolutions.gopreach.data.model.InterestedPerson]/[com
 * .emfitsolutions.gopreach.data.model.Person] — plain names, not PSGC ids,
 * matching every other free-text location field this app already stores
 * (e.g. [com.emfitsolutions.gopreach.data.model.InterestedPerson.address]);
 * the ids only ever matter transiently, for cascading the next dropdown. */
data class PhilippineAddressSelection(
    val province: String? = null,
    val cityMunicipality: String? = null,
    val barangay: String? = null,
)

private fun normalize(s: String): String =
    s.replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("[^A-Za-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .uppercase()

/**
 * "Add a dropdown for City, Municipalities, Town Barangay... The publisher
 * will browse manually, however it can be automatic if the publisher will
 * capture the coordinates" — the manual-browse half wraps [PsgcDao]'s
 * type-ahead search; the automatic half ([resolveFromGeocode]) takes
 * [GeocodedAddress] (Android's on-device Geocoder output, already best-
 * effort per its own doc comment) and matches each level against the same
 * bundled PSGC table, falling back level by level (exact match, then a
 * loose "contains" search) so a slightly different capitalization/wording
 * from the geocoder still resolves to a real, canonical PSGC entry rather
 * than silently matching nothing.
 */
@Singleton
class PhilippineLocationRepository @Inject constructor(
    private val dao: PsgcDao,
) {
    suspend fun searchProvinces(query: String): List<PsgcOption> =
        dao.searchProvinces(normalize(query)).map { it.toOption() }

    suspend fun searchCitiesMunicipalities(provinceId: Int?, query: String): List<PsgcOption> =
        (if (provinceId != null) dao.searchMuncitiesInProvince(provinceId, normalize(query)) else dao.searchMuncitiesNationwide(normalize(query)))
            .map { it.toOption() }

    suspend fun searchBarangays(muncityId: Int, query: String): List<PsgcOption> =
        dao.searchBarangaysInMuncity(muncityId, normalize(query)).map { it.toOption() }

    /** Territory Map's "Municipality: All Municipalities" case — every
     * barangay across the whole province at once. */
    suspend fun searchBarangaysInProvince(provinceId: Int, query: String): List<PsgcOption> =
        dao.searchBarangaysInProvince(provinceId, normalize(query)).map { it.toOption() }

    /** Best-effort match of a Geocoder result onto real PSGC rows — returns
     * both the resolved [PhilippineAddressSelection] (names, for display/
     * saving) and the matched ids (so the picker can keep cascading from
     * here, e.g. if the publisher wants to correct just the barangay). Any
     * level the geocoder didn't return, or that couldn't be matched, is
     * simply left null rather than guessed — see [PhilippineAddressPicker]
     * for how an unmatched level still falls back to manual browsing. */
    suspend fun resolveFromGeocode(geocoded: GeocodedAddress): ResolvedAddress {
        val province = geocoded.province?.let { findProvince(it) }
        val muncity = geocoded.cityMunicipality?.let { findMuncity(it, province?.id) }
        val barangay = if (muncity != null) geocoded.barangay?.let { findBarangay(it, muncity.id) } else null
        return ResolvedAddress(
            provinceId = province?.id,
            provinceName = province?.name,
            muncityId = muncity?.id,
            muncityName = muncity?.name,
            barangayId = barangay?.id,
            barangayName = barangay?.name,
        )
    }

    /** Public re-hydration lookups — used by [com.emfitsolutions.gopreach.ui
     * .components.PhilippineAddressPicker] to recover a row's id from a
     * plain name it was only ever given as text (a loaded record, or a name
     * set by [resolveFromGeocode] without its id persisted anywhere), so it
     * knows which province/muncity to scope the next dropdown's search to. */
    suspend fun findProvinceByName(rawName: String): PsgcOption? = findProvince(rawName)?.toOption()
    suspend fun findMuncityByName(rawName: String, provinceId: Int?): PsgcOption? = findMuncity(rawName, provinceId)?.toOption()

    private suspend fun findProvince(rawName: String): ProvinceEntity? {
        val n = normalize(rawName)
        return dao.provinceByExactName(n) ?: dao.searchProvinces(n).firstOrNull()
    }

    private suspend fun findMuncity(rawName: String, provinceId: Int?): MuncityEntity? {
        val n = normalize(rawName)
        dao.muncityByExactName(n, provinceId)?.let { return it }
        val candidates = if (provinceId != null) dao.searchMuncitiesInProvince(provinceId, n) else dao.searchMuncitiesNationwide(n)
        return candidates.firstOrNull()
    }

    private suspend fun findBarangay(rawName: String, muncityId: Int): BarangayEntity? {
        val n = normalize(rawName)
        return dao.barangayByExactName(n, muncityId) ?: dao.searchBarangaysInMuncity(muncityId, n).firstOrNull()
    }

    private fun ProvinceEntity.toOption() = PsgcOption(id, name)
    private fun MuncityEntity.toOption() = PsgcOption(id, name)
    private fun BarangayEntity.toOption() = PsgcOption(id, name)
}

/** Result of [PhilippineLocationRepository.resolveFromGeocode] — ids
 * included alongside names so the caller's picker state can keep cascading
 * (e.g. re-querying barangays for [muncityId]) without a second lookup. */
data class ResolvedAddress(
    val provinceId: Int?,
    val provinceName: String?,
    val muncityId: Int?,
    val muncityName: String?,
    val barangayId: Int?,
    val barangayName: String?,
) {
    fun toSelection() = PhilippineAddressSelection(province = provinceName, cityMunicipality = muncityName, barangay = barangayName)
}
