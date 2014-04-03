// honouring Jeff's MKeys by keeping the M for prototyping the new Ktl


MKtl : MAbstractKtl { // abstract class
	classvar <deviceDescriptionFolder; //path of MKtlSpecs folder
	classvar <allDevDescs; // contains the identity dictionary in index.desc.scd
	//i.e. (BCR2000 -> ( 'osx': ( 'device': BCR2000 ), 'device': BCR2000, 'protocol': midi, 'file': BCR2000.desc.scd ))
	classvar <all; // holds all instances of MKtl
	classvar <specs; // ( 'specName': ControlSpec, ...) -> all specs
	classvar <allAvailable; // ( 'midi': List['name1',... ], 'hid': List['name1',... ], ... )

	// tree structure composed of dictionaries and arrays with a description of all the elements on the device.
	// read from an external file.
	var <deviceDescriptionHierarch;

	// an array of keys and values with a description of all the elements on the device.
	// generated from the hierarchical description read from the file.
	var <deviceDescription;
	                    //of type: [ 'elemName', ( 'mode': Symbol, 'chan': Int, 'type': Symbol, 'specName': Symbol,
                        //'midiMsgType': Symbol, 'spec':ControlSpec, 'ccNum': Int )
	                    // i.e. [ prA1, ( 'mode': toggle, 'chan': 0, 'type': button, 'specName': midiBut,
                        //'midiMsgType': cc, 'spec': a ControlSpec(0, 127, 'linear', 127, 0, ""), 'ccNum': 105 )


	//var <>recordFunc; // what to do to record incoming control changes

	classvar <exploring = false;

	*initClass {
		Class.initClassTree(Spec);
		all = ();
		allAvailable = ();

		specs = ().parent_(Spec.specs);

		// general
		this.addSpec(\cent255, [0, 255, \lin, 1, 128]);
		this.addSpec(\cent255inv, [255, 0, \lin, 1, 128]);
		this.addSpec(\lin255,  [0, 255, \lin, 1, 0]);
		this.addSpec(\cent1,  [0, 1, \lin, 0, 0.5]);
		this.addSpec(\lin1inv,  [1, 0, \lin, 0, 1]);

		// MIDI
		this.addSpec(\midiNote, [0, 127, \lin, 1, 0]);
		this.addSpec(\midiCC, [0, 127, \lin, 1, 0]);
		this.addSpec(\midiVel, [0, 127, \lin, 1, 0]);
		this.addSpec(\midiBut, [0, 127, \lin, 127, 0]);
		this.addSpec(\midiTouch, [0, 127, \lin, 1, 0]);
		this.addSpec(\midiBend, [0, 16383, \lin, 1, 8192]);

		// HID
		this.addSpec(\hidBut, [0, 1, \lin, 1, 0]);
		this.addSpec(\hidHat, [0, 1, \lin, 1, 0]);
		this.addSpec(\compass8, [0, 8, \lin, 1, 1]); // probably wrong, check again!

		this.addSpec(\cent1,  [0, 1, \lin, 0, 0.5].asSpec);
		this.addSpec(\cent1inv,  [1, 0, \lin, 0, 0.5].asSpec);

		deviceDescriptionFolder = this.filenameSymbol.asString.dirname.dirname +/+ "MKtlSpecs";
	}

	*find { |protocols|
		if ( protocols.isNil ){
			this.allSubclasses.do(_.find( post: false ) );
			"\n-----------------------------------------------------".postln;
			this.allSubclasses.do(_.postPossible() );
		}{
			if ( protocols.isKindOf( Array ) ){
				protocols.do{ |pcol|
					switch( pcol,
						\hid,  { HIDMKtl.find},
						\midi, { MIDIMKtl.find}
					);
				};
			};
			if ( protocols.isKindOf( Symbol ) ){
				switch( protocols,
					\hid,  { HIDMKtl.find},
					\midi, { MIDIMKtl.find}
				);
			};
		};
	}

	*addSpec {|key, spec|
		specs.put(key, spec.asSpec);
	}

	*makeShortName {|deviceID|
		^(deviceID.asString.toLower.select{|c| c.isAlpha && { c.isVowel.not }}.keep(4) ++ deviceID.asString.select({|c| c.isDecDigit}))
	}

		// new returns existing instances
		// of subclasses that exist in .all,
		// or returns a new empty instance.
		// this is to allow virtual MKtls eventually.
	*new { |name, deviceDescName|
		var devDesc;
		if ( this.checkName( name, deviceDescName ).not ){
			^nil;
		};
		if (deviceDescName.isNil) {
			if ( all[name].notNil ){
				^all[name];
			};
			// it does not exist yet, but maybe it has a spec that fits?
			// there is no device description, but maybe this was an autogenerated name for a device found, so let's open it
			// look in the allavailable dict
			allAvailable.keysValuesDo{ |proto,list|
				if ( list.includes( name ) ){
					proto.switch(
						\midi, { ^MIDIMKtl.new( name ) },
						\hid, { ^HIDMKtl.new( name ) }
						// add the other protocols
					);
				};
			}
		};
		// create an instance of the right subclass based on the protocol given in the device description
		devDesc = this.getDeviceDescription( deviceDescName );
		devDesc[ \protocol ].switch(
			\midi, { ^MIDIMKtl.newFromDesc( name, deviceDescName, devDesc ) },
			\hid, { ^HIDMKtl.newFromDesc( name, deviceDescName, devDesc ) }
			//\osc, ^OSCMKtl.new( name, deviceDesc ),
			//\serial, ^SerialMKtl.new(name, deviceDesc )
		);

		^this.basicNew(name, deviceDescName);
	}

	*basicNew { |name, deviceDescName|
		^super.new.init(name, deviceDescName);
	}

	*make { |name, deviceDescName|
		if (all[name].notNil ) {
			warn("MKtl name '%' is in use already. Please use another name."
				.format(name));
			^nil
		};
		^this.basicNew( name, deviceDescName )
	}

	*checkName { |name, deviceDescName|
		if (all[name].notNil and: deviceDescName.notNil ) {
			warn("MKtl name '%' is in use already. Please use another name."
				.format(name));
			^false
		};
		^true
	}

	/*

	init procedure:

	- load description from file into deviceDescription var
	- flattenDescription on each element
	- substitute each spec in the element for the real ControlSpec corresponding to it


	*/
	init { |argName, deviceDescName|
		name = argName;

		//envir = ();
		elementsDict = ();
		if (deviceDescName.isNil) {
			warn("no deviceDescription name given!");
		} {
			this.loadDeviceDescription(deviceDescName);
			if ( deviceDescription.notNil ){
				this.makeElements;
				( "Opened device:" + name + "using device description"+deviceDescName ).postln;
			};
		};
		all.put(name, this);
	}

	storeArgs { ^[name] }
	printOn { |stream| this.storeOn(stream) }

	*loadDeviceIndex { |reload=false|
		var path;
		if ( allDevDescs.isNil or: reload ){
			path = deviceDescriptionFolder +/+ "index.desc.scd";
			allDevDescs = try {
				path.load;
			} {
				"//" + this.class ++ ": - no device description index found!\n"
				.post;
			};
		};
	}

	*getDeviceDescription { |devName|
		var devDesc;
		this.loadDeviceIndex;
		devDesc = allDevDescs.at( devName );
		if ( devDesc.isNil ){
			// see if we can find it from the device name:
			allDevDescs.keysValuesDo{ |key,desc|
				if ( desc[\type] != \template ){
					desc = this.flattenDescription( desc );
					if ( desc[ \device ] == devName ){
						devDesc = desc;
					};
				};
			};
		};
		^devDesc;
	}

	loadDeviceDescription { |deviceName|
		var deviceInfo;
		var deviceFileName;
		var path;

		// look the filename up in the index
		deviceInfo = this.class.getDeviceDescription( deviceName );

		if ( deviceInfo.notNil ){

			// deviceInfo also has information about protocol and os specific naming

			deviceFileName = deviceInfo[ \file ];

			path = deviceDescriptionFolder +/+ deviceFileName;

			deviceDescriptionHierarch = try {
				path.load;
			} {
				"%: - no device description found for %: please make it!\n"
				.postf(this.class, deviceName);
				//	this.class.openTester(this);
			};

			deviceDescription = this.makeFlatDeviceDescription( deviceDescriptionHierarch );

			deviceDescription.pairsDo{ |key,elem|
				this.class.flattenDescription( elem )
			};

			// create specs
			deviceDescription.pairsDo {|key, elem|
				var foundSpec =  specs[elem[\spec]];
				if (foundSpec.isNil) {
					warn("Mktl - in description %, el %, spec for '%' is missing! please add it via:"
						"\nMktl.addSpec( '%', [min, max, warp, step, default]);\n"
						.format(deviceName, key, elem[\spec], elem[\spec])
				);
				};
				elem[\specName] = elem[\spec];
				elem[\spec] = this.class.specs[elem[\specName]];
			};
		}{ // no device file found:
			this.warnNoDeviceFileFound(deviceName);
		}
	}

	//traversal function for combinations of dictionaries and arrays
	prTraverse {
		var isLeaf = { |dict|
			dict.values.any({|x| x.size > 1}).not
		};

		var f = { |x, state, stateFuncOnNodes, leafFunc|

			if(x.isKindOf(Dictionary) ){
				if( isLeaf.(x) ) {
					leafFunc.( state , x )
				}{
					x.collect{ |val, key|
						f.(val, stateFuncOnNodes.(state, key), stateFuncOnNodes, leafFunc )
					}
				}
			} {
				if(x.isKindOf(Array) ) {
					x.collect{ |val, i|
						f.(val, stateFuncOnNodes.(state, i),  stateFuncOnNodes, leafFunc )
					}
				} {
					Error("MKtl:prTraverse Illegal data structure in device description.\nGot object % of type %. Only allowed objects are Arrays and Dictionaries".format(x,x.class)).throw
				}
			}

		};
		^f
	}

	explore{ |mode=true|
		this.subclassResponsibility(thisMethod)
	}

	prUnderscorify {
		^{ |a,b|
			var c = if(b.isNumber){b+1}{b};
			if(a != "") {
				a++"_"++c.asString
			} {
				c.asString
			}
		};
	}

	makeFlatDeviceDescription { |devDesc|

		var flatDict = ();

		this.prTraverse.(devDesc, "", this.prUnderscorify, { |state, x| flatDict.put(state.asSymbol,  x) } );

		^flatDict.asKeyValuePairs

	}

	*postAllDescriptions {
		(MKtl.deviceDescriptionFolder +/+ "*").pathMatch
			.collect { |path| path.basename.splitext.first }
			.reject(_.beginsWith("_"))
			.do { |path| ("['" ++ path ++"']").postln }
	}

	*flattenDescription { |devDesc|
		// some descriptions may have os specific entries, we flatten those into the dictionary
		var platformDesc = devDesc[ thisProcess.platform.name ];
		if ( platformDesc.notNil ){
			platformDesc.keysValuesDo{ |key,val|
				devDesc.put( key, val );
			}
		};
		^devDesc;
	}

	*flattenDescriptionForIO { |eleDesc,ioType|
		// some descriptions may have ioType specific entries, we flatten those into the dictionary
		var ioDesc = eleDesc[ thisProcess.platform.name ];
		if ( ioDesc.notNil ){
			ioDesc.keysValuesDo{ |key,val|
				eleDesc.put( key, val );
			}
		};
		^eleDesc;
	}


	elementDescriptionFor { |elname|
		^deviceDescription[deviceDescription.indexOf(elname) + 1]
	}

	postDeviceDescription {
		deviceDescription.pairsDo {|a, b| "% : %\n".postf(a, b); }
	}

	makeElements {
		var leafFunc;
		this.elementNames.do{|key|
			elementsDict[key] = MKtlElement(this, key);
		};
		leafFunc = { |finalKey, x| elementsDict[finalKey.asSymbol] };
		elements = this.prTraverse.(deviceDescriptionHierarch, "", this.prUnderscorify, leafFunc );

	}

	// needed for fixing elements that are not present for a specific OS
	replaceElements{ |newelements|
		elementsDict = newelements;
	}

		// convenience methods
	defaultValueFor { |elName|
		^this.elementsDict[elName].defaultValue
	}

	makeElementName { |args|
		var n = args.size;
		^(args[..n-2].collect({ |x| x.asString++"_"}).reduce('++')++args.last).asSymbol
	}

	elementAt { |...args|
		^elements.deepAt1(*args)
	}

	// should filter: those for my platform only
	elementNames {
		if ( elementsDict.isEmpty ){
			^(0, 2 .. deviceDescription.size - 2).collect (deviceDescription[_])
		}{
			^elementsDict.keys.asArray;
		}
	}

	elementsOfType { |type|
		^elementsDict.select { |elem|
   			elem.elementDescription[\type] == type
		}
	}

	elementsNotOfType { |type|
		^elementsDict.select { |elem|
   			elem.elementDescription[\type] != type
		}
	}

	allElements {
		^elementsDict.asArray
	}

}
