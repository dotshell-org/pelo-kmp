package eu.dotshell.pelo.generic.data.config

import eu.dotshell.pelo.generic.data.network.transport.TransportConfig

class AppTransportConfig(private val data: TransportConfigData) : TransportConfig {
    override val baseUrl: String = data.baseUrl
    override val networkName: String = data.networkName
    override val region: String = data.region
    override val organizingAuthority: String = data.organizingAuthority
    override val dataSource: String = data.dataSource
    override val dataSourceUrl: String = data.dataSourceUrl
    override val dataLicense: String = data.dataLicense
    override val regionBounds: DoubleArray = data.regionBounds.toDoubleArray()
    override val offlineMapZoomRange: IntRange = data.offlineMapZoomRange.start..data.offlineMapZoomRange.end
    override val schoolHolidaysFile: String = data.schoolHolidaysFile
    override val primaryColor: String = data.primaryColor
    override val secondaryColor: String = data.secondaryColor
}
