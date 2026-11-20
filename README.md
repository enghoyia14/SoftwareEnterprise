# CSC8104 Quickstart Project

This quickstart project is provided for students on the CSC8104 Enterprise Middleware module and provides a foundation for starting their coursework. Students are expected to download and build their solution within the provided project and should not aim to create a new project from scratch.

Within the project there is an example REST service for creating and storing contacts which can be accessed via the Swagger UI endpoint (http://localhost:8080/q/swagger-ui). It is encouraged that students spend spend time reading through this code to gain a strong understanding of how the project works. Not only this, but students are also encouraged to follow a similar packaging structure.

Students are not required to remove the contact service and can leave this functionality in their submission.

Throughout the coursework specification there are many links to various guides to help you complete the coursework. It is strongly encouraged that you spend time working through these guides before attempting to implement the specification requirements.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev -D"net.bytebuddy.experimental"
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

### University machines: fixing a `JAVA_HOME` error

If you are on a university machine and get a `JAVA_HOME` error when running the command above, run the following PowerShell command first:

```powershell
$env:JAVA_HOME = (Get-Command javac).Source | Split-Path | Split-Path
```

This finds the installed JDK and sets `JAVA_HOME` for the current terminal session only. **You will need to run it each time you open a new terminal on the university machines.**

## Run and test the Taxi and TravelAgent APIs

Run these commands from the project folder in PowerShell. Java 17 is required.

```powershell
.\mvnw.cmd quarkus:dev -D"net.bytebuddy.experimental"
```

Open Swagger UI at <http://localhost:8080/q/swagger-ui/>. The local development database is the H2 file `data/csc8104`; development uses Hibernate `update`, so restarting the application preserves its records. Do not change the database URL or use a database reset command when you need to keep your coursework data.

The complete automated suite runs with:

```powershell
.\mvnw.cmd test
```

Tests use the test profile's in-memory H2 database and mock the remote Hotel and Flight clients. They do not use the local development database or create remote service records.

## TravelAgent service configuration

The local defaults are in `src/main/resources/application.properties`. They can be overridden in the same PowerShell terminal before starting Quarkus:

| Setting | Current default | Purpose |
| --- | --- | --- |
| `TAXI_API_URL` | `http://localhost:8080` | This local Taxi API |
| `HOTEL_API_URL` | `https://ncl-csc-8104-athulya-reji-git-athulyaraj-dev.apps.rm2.thpm.p1.openshiftapps.com` | Hotel service |
| `FLIGHT_API_URL` | `https://csc-8104-enterprise-java-yogiraj-sadhan-yogiraj-max-dev.apps.rm2.thpm.p1.openshiftapps.com` | Flight service |
| `TRAVELAGENT_TAXI_CUSTOMER_ID` | `2` | Pre-created TravelAgent customer in Taxi |
| `TRAVELAGENT_HOTEL_CUSTOMER_ID` | `1` | Pre-created TravelAgent customer in Hotel |
| `TRAVELAGENT_FLIGHT_CUSTOMER_ID` | `1` | Pre-created TravelAgent customer in Flight; verify before live use |

Use the service owners' current records and API documentation to confirm IDs. IDs belong to their own service: the request's `customerId` is a customer in this local Taxi/TravelAgent database, while the configured customer IDs above are sent to the respective services. The request also needs an existing Taxi, Hotel and Flight ID and a future date.

**Hotel API contract and current records (checked 20 November 2026):** the selected Hotel service OpenAPI contract defines `GET /customers` and `GET /hotels` collection operations, but does not define `GET /customers/{id}` or `GET /hotels/{id}`. The TravelAgent therefore reads those collections and checks for the requested numeric ID before booking. The live `GET /customers` and `GET /hotels` responses were both `[]`; the configured Hotel customer 1 and Hotel 1 are currently unavailable. The integration cannot make a live Hotel booking until the service owner restores records in persistent storage. This project does not create, delete, or reset Hotel customer/hotel records.

**Flight API contract and current records (checked 20 November 2026):** the Flight OpenAPI contract supports `GET /customers/{id}` and `GET /flights/{id}`. `GET /customers` and `GET /flights` returned `[]`; individual GETs for configured customer 1 and Flight 1 returned “No Customer/Flight with the id 1 was found!”. These configured IDs are currently unavailable. This project does not create, delete, or reset Flight customer/flight records.

**Production storage and deployed URL:** local development uses the file-backed H2 database `data/csc8104`, and that database file exists. The production profile currently falls back to `jdbc:h2:mem:default`; there is no persistent-volume or database-service manifest in this project, so production records are not guaranteed to survive a pod restart or redeployment. Hibernate schema generation was changed from destructive `drop-and-create` to `update`, but this does not make an in-memory database persistent. The deployed Taxi URL currently exposes only the Contacts endpoints in `/q/openapi`; it is not yet serving this Taxi/TravelAgent coursework build. No deployment was made.

The earlier cancellation used DELETE requests only against Taxi, Hotel and Flight `/bookings/{id}` paths; it sent no DELETE request to a customer, hotel or flight resource path. Always verify the current remote IDs immediately before a live booking.

## TravelAgent Swagger workflow

The TravelAgent endpoints are:

* `POST /travelagent/bookings` — creates Taxi, then Hotel, then Flight bookings and returns the aggregate ID.
* `GET /travelagent/bookings/{id}` — retrieves one aggregate booking.
* `GET /travelagent/bookings` — lists aggregates; optional `customerId` filters by the local customer.
* `DELETE /travelagent/bookings/{id}` — cancels Flight, then Hotel, then Taxi. A 404 from Hotel or Flight means that booking was already cancelled and allows a retry to continue. The aggregate remains while cancellation is incomplete; after Taxi deletion succeeds, the required Taxi relationship's database cascade removes the aggregate.

Example body for a deliberate end-to-end POST (replace the IDs/date with records verified immediately before the demo):

```json
{
  "customerId": 2,
  "taxiId": 2,
  "hotelId": 1,
  "flightId": 1,
  "bookingDate": "YYYY-MM-DD"
}
```

This POST creates real records in all three services. Use it only when you intend to create a new combined booking. Save the returned aggregate `id` for GET and DELETE.

At startup, `TravelAgentBookingSchemaMigration` checks the Taxi-booking foreign key and upgrades its delete rule to cascade if needed. It only changes the relationship constraint; it does not reset or remove existing rows.
