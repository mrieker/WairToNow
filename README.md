# WairToNow

Android app covers areas covered by US charts, including sectionals, terminal
area charts and helicopter charts.  Also included is mapping via OpenStreetMaps
for walking/biking to nearby $100 hamburger stands, as well as georeferenced
approach plates and airport diagrams.  Also provides OpenStreetMap-backed
runway diagrams for all airports, though they are primarily used for those
airports that don't have official FAA-provided airport diagram plates.
Approach plates include an optional on-screen DME display that the user can
configure, and can draw out the approach path if enabled.

Also included are recent OpenFlightMaps charts and waypoints covering parts
of Europe.  These are unofficial VFR charts.  There are also the usual WairToNow
runway diagrams provided for airports that have runway lat,lon information given
in the OpenFlightMaps database.  See https://www.openflightmaps.org for more
information.

The official approach plates and runway diagrams provided by Eurocontrol are
available in the app.

There is also access to recent waypoints from ourairports.com giving many
waypoints around the world.  This database does not include any charts.  And,
as with OpenFlightMaps, these waypoints are unofficial.

Server provides scripts to populate and operate a server, fetching the required
data from online sources.  The scripts should be run every 28 days.
