/*
* start on RME fireface by OSC support:
- implement first message from totalmix osc table,
- add others later


// test direct totalmix osc remote control:
f = NetAddr.localAddr.port = 7001;
f.sendMsg("/1/volume1", 1.0.rand);
f.sendMsg("/1/volume8", 1.0.rand);

// test first message by MKtl:
m = MKtl(\ttm, "rme-audio-totalmix");
m.elAt(\invol).choose.value_(1.0.rand);
*/

(
deviceName: "TotalMix",
idInfo: "TotalMix",
protocol: \osc,
netAddrInfo: (srcPort: 9001, destPort: 7001, ipAddress: "localhost"),

elementsDesc: (
	elements: [
		(
			key: \invol,
			shared: (elementType: \fader, ioType: \inout,
				spec: \unipolar),
			elements: (1..8).collect { |i|
				(oscPath: "/1/volume%".format(i));
			}
		),
		/// more commands to follow here
	]
)
)
