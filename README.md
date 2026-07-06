<p align="center">
  <img src="massilia-icon.png" alt="Massilia Logo" width="256" height="256" />
</p>

<h1 style="text-align: center;">Massilia</h1>

<p style="text-align: center;">
<strong>Public transport app for Marseille and its surroundings</strong>
</p>

---

## About

**Massilia** is a modern, open-source Kotlin Multiplatform application (Android and iOS) designed to easily navigate the Marseille public transport network (RTM): metro, tramway, bus, BHNS and sea shuttles. Journey planning and schedules work fully offline from bundled GTFS data.

### Prerequisites

* Android 7.0 (API 24) or higher
* Android Studio Ladybug or higher
* JDK 11

### Transit data

Transit data comes from the RTM GTFS feed published on [transport.data.gouv.fr](https://transport.data.gouv.fr) (ODbL license), preprocessed with `raptor-gtfs-pipeline` into the binary files bundled under `app/src/commonMain/composeResources/files/raptor/`. Line pictograms are reproduced from the official RTM network map via [Wikimedia Commons](https://commons.wikimedia.org/wiki/Category:Public_transport_symbols_of_Marseille) (public domain); they can be regenerated with `tools/fetch_rtm_icons.py`.
