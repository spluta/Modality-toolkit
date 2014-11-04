MKtlAbstractElementGroup : MAbstractElement {
	
	var <elements;

	*new { |source, name, elements|
		^super.newCopyArgs( source, name ).elements_(elements);
	}
	
	init { }
	
	elements_ { |newElements|
		elements = newElements;
		this.init;
	}
	
	at { |index| ^elements[index] }
	
	put { |index, element|
		this.elements = this.elements.put( index, element );
	}
	
	add { |element|
		this.elements = this.elements.add( element );
	}
	
	size { ^elements.size }
	
	removeAll {
		elements.do(_.prRemoveGroup( this ));
		this.elements = nil;
	}
	
	remove { |element|
		element.prRemoveGroup( this );
		 ^this.elements.remove( element );
	}
	
	indexOf { |element| ^this.elements.indexOf( element ); }
	
	do { |function| elements.do( function ); }
	
	flat {
		^this.elements.flat;
	}
	
	prFlat { |list|
		this.do({ arg item, i;
			if (item.respondsTo('prFlat'), {
				list = item.prFlat(list);
			},{
				list = list.add(item);
			});
		});
		^list
	}
	
	value { ^elements.collect(_.value) }

	addAction { |argAction|
		action = action.addFunc(argAction);
	}

	removeAction { |argAction|
		action = action.removeFunc(argAction);
	}

	reset {
		action = nil
	}

	doAction { |...children|
		children = children.add( this );
		action.value( *children );
		parent !? _.doAction( *children );
		groups.do( _.doAction( *children ) );
	}

}

MKtlElementArray : MKtlAbstractElementGroup {
	
	init { 
		elements = elements ?? {[]};
		elements.do(_.prAddGroup(this));
	}
	
	elements_ { |newElements|
		elements = newElements.asArray;
		this.init;
	}

}

MKtlElementDict : MKtlAbstractElementGroup {
	
	var <>keys;
	
	init { 
		elements = elements ?? {()};
		keys = keys ? [];
		if( elements.size < keys.size ) {
			elements.keys.asArray.do({ |key|
				if( keys.includes( key ).not ) {
					keys = keys.add( key );
				};
			});
		};
		elements.do(_.prAddGroup(this));
	}
	
	elements_ { |newElements|
		elements = newElements; // this should be made into a dictionary somehow
		this.init;
	}
	
	flat { ^this.elements.values.flat }
	
	flatSize { ^this.values.flatSize }
	
	at { |index| 
		if( index.isNumber ) { index = keys[ index ] };
		^elements[index] 
	}
	
	put { |key, element|
		if( keys.includes( key ).not ) {
			keys = keys.add( key );
		};
		this.elements = this.elements.put( key, element );
	}

	add { |association|
		this.put( association.key, association.value );
	}
	
	indexOf { |element|
		^elements.findKeyForValue( element );
	}

}