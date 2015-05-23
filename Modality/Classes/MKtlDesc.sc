/*
PLANS:

* support array of associations as incoming dict!
* best as instVar and sync convert the other to a dict
* make a directory cache of filenames -> devicenames as reported
* update only when files newer than cache were added
*/

MKtlDesc {
	classvar <defaultFolder, <folderName = "MKtlDescriptions";
	classvar <fileExt = ".desc.scd";
	classvar <descFolders;
	classvar <allDescs;

	classvar <>isElementTestFunc;

	var <fullDesc, <path, <name, <>elementsAssocArray;

	*initClass {
		defaultFolder = this.filenameSymbol.asString.dirname.dirname
			+/+ folderName;
		descFolders = List[defaultFolder];
		allDescs =();
		isElementTestFunc = { |el| el.isKindOf(Dictionary) and: { el[\spec].notNil } };
	}

	*addFolder { |path, name = (folderName)|
		var folderPath = path +/+ name;
		var foundFolder = pathMatch(folderPath);

		if (descFolders.includesEqual(folderPath)) { ^this };
		descFolders.add(folderPath);
		if (foundFolder.notEmpty) {
			"MKtlDesc found and added folder: %\n".postf(foundFolder.cs);
		} {
			"// MKtlDesc added a nonexistent folder: %\n.".postf(name.cs);
			"// you can create it with:"
			"\n File.mkdir(\"%\".standardizePath);\n".postf(folderPath);
		}
	}

	*openFolder { |index = 0|
		descFolders[index].openOS;
	}

	*findFile { |filename = "*", folderIndex, postFound = false|
		var foundPaths, foldersToLoadFrom, plural = 0;
		folderIndex = folderIndex ?? { (0 .. descFolders.size-1) };
		foldersToLoadFrom = descFolders[folderIndex.asArray].select(_.notNil);

		foundPaths = descFolders.collect { |dir|
			(dir +/+ filename ++ fileExt).pathMatch
		}.flatten(1);

		if (postFound) {
			plural = if (foundPaths.size == 1, "s", "");
			"\n*** MKtlDesc.findFile found % file% for'%': ***\n"
			.postf(foundPaths.size, plural, filename);
			foundPaths.printcsAll; "".postln;
		}

		^foundPaths
	}

	// convenience only
	*loadDescs { |filename = "*", folderIndex|
		var paths = this.findFile(filename, folderIndex);
		paths.do {|path|
			this.fromPath(path);
		};
		"\n// MKtlDesc loaded % description files - see:"
		"\nMKtlDesc.allDescs;\n".postf(paths.size);
	}

	*fromFileName { |filename, folderIndex|
		var paths = this.findFile(filename, folderIndex, false);
		if (paths.isEmpty) {
			warn("MktlDesc: could not find desc with filename %.\n"
				.format(filename));
			^nil;
		};
		if (paths.size > 1) {
			warn("MktlDesc: found multiple files, loading only first: %.\n"
				.format(paths[0].basename));
		};
		^this.fromPath(paths[0]);
	}

	*fromPath { |path|
		var desc = path.load;
		if (desc.isNil) {
			warn("MktlDesc: could not load desc from path %.\n"
				.format(path));
			^nil;
		};
		if (this.isValidDescDict(desc)) {
			desc.path = path;
			desc.filename = path.basename.drop(fileExt.size.neg);
			^this.fromDict(desc);
		}
	}

	*fromDict { |dict|
		if (this.isValidDescDict(dict).not) {
			warn("MKtlDesc - dict is not a valid description: %"
				.format(dict));
			^nil
		};
		^super.new.fullDesc_(dict);
	}

	*at { |descName|
		^allDescs[descName]
	}

	*postLoaded {
		"\n*** MKtlDesc - loaded descs: ***".postln;
		allDescs.keys.asArray.sort.do { |key|
			"% // %\n".postf(allDescs[key], allDescs[key].idInfo);
		};
		"******\n".postln;
	}

	// according to current definition,
	// \idInfo, \protocol, \description are required;
	// can add more tests here,
	// e.g. check whether description is wellformed
	*isValidDescDict { |dict|
		^dict.isKindOf(Dictionary)
		or: { dict.isAssociationArray
		and: { dict[\idInfo].notNil
		and: { dict[\protocol].notNil
		and: { dict[\description].notNil
	//	and: { this.checkElementsDesc(dict) }
					}
				}
			}
		}
	}

	// write tests for this later
	*checkElementsDesc { |desc|
		^true
	}

	*new { |name|
		^this.at(name.asSymbol) ?? {
			this.fromFileName(name);
		}
	}

	fullDesc_ { |dict|
		if (this.class.isValidDescDict(dict)) {
			this.prMakeElemColls(dict);
			name = name ?? {
				dict[\filename].asSymbol ?? {
					dict[\idInfo].asSymbol
			}};
			fullDesc = dict;
			this.init;
		};
	}

	prMakeElemColls { |indict|
		if (indict.isKindOf(Dictionary)) {
			elementsAssocArray = indict.asAssociations;
		};
		if (indict.isAssociationArray) {
			elementsAssocArray = indict;
			this.elementsDesc = indict.asDict.as(Event);
		};
	}

	init { |filename|
		filename = filename ?? { fullDesc[\path] };
		name = name ?? {
			if (filename.notNil) { filename.asSymbol; }
		};
		if (name.isNil) {
			warn("MKtlDesc: no name given, so cannot be in allDescs.".format(path));
		} {
			allDescs.put (name, this);
		};
		this.makeElementsArray;
		this.resolveDescEntriesForPlatform;
	}

	openFile {
		if (path.notNil) { path.asString.openDocument };
	}

	// keep all data in fullDesc only if possible
	protocol { ^fullDesc[\protocol] }
	protocol_ { |type| ^fullDesc[\protocol] = type }

	// adc proposal - seem clearest
	// idInfoAsReportedBySystem,
	// aslo put it in fullDesc[\idInfo]
	idInfo { ^fullDesc[\idInfo] }
	idInfo_ { |type| ^fullDesc[\idInfo] = type }

	elementsDesc { ^fullDesc[\description] }
	elementsDesc_ { |type| ^fullDesc[\description] = type }

	deviceFilename {
		^path !? { path.basename.drop(fileExt.size.neg) }
	}

	postInfo { |elements = false|
		("---\n//" + this + $:) .postln;
		"deviceFilename: %\n".postf(this.deviceFilename);
		"protocol: %\n".postf(this.protocol);
		"deviceIDString: %\n".postf(this.deviceIDString);
		"desc keys: %\n".postf(this.elementsDesc.keys);


		if (elements) { this.postElements }
	}

	writeFile { |path|
		"! more than nice to have ! - not done yet.".postln;
	}

	storeArgs { ^[name] }
	printOn { |stream|
		stream << this.class.name << ".at(%)".format(name.cs);
	}

	*resolveForPlatform { |dict|
		var platForms = [\osx, \linux, \win];
		var myPlatform = thisProcess.platform.name;

		dict.keysValuesDo { |dictkey, entry|
			var foundPlatformDep = false, foundval;
			if (entry.isKindOf(Dictionary)) {
				foundPlatformDep = entry.keys.sect(platForms).notEmpty;
			};
			if (foundPlatformDep) {
				foundval = entry[myPlatform];
				"MKtlDesc replacing: ".post;
				dict.put(*[dictkey, foundval].postln);
			};
		}
		^dict
	}

	// (-: just in case programming ;-)
	resolveDescEntriesForPlatform {
		this.class.resolveForPlatform(fullDesc);
		this.elementsDesc.keysValuesDo { |key, elemDesc|
			MKtlDesc.resolveForPlatform(elemDesc);
		};
		this.class.resolveForPlatform(this.elementsDesc);
	}

	postElements {
		this.elementsDesc.traverseDo({ |el, deepKeys|
			deepKeys.size.do { $\t.post };
			deepKeys.postcs;
		}, (_.isKindOf(Dictionary)));
	}

	makeElementsArray { |devDesc|
		var arr = [];
		this.elementsDesc.traverseDo({ |el, deepKeys|
			var elKey = deepKeys.join($_).asSymbol;
			arr = arr.add(elKey -> el);
		}, isElementTestFunc);
		elementsAssocArray = arr;
	}
}
