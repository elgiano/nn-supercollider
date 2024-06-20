+NN {
	*copyDLLs {
		Platform.case { \windows } {
			(NN.filenameSymbol.asString.dirname +/+ "../ignore/*.dll").pathMatch.do { |dll|
				try {
					File.copy(dll, Platform.resourceDir);
					"*** % copied".format(dll.basename).postln;
				} { |err|
					err.errorString.postln;
				}
			}
		} {
			warn("You don't need to copy DLLs unless you're on Windows");
		};
	}

	*deleteDLLs {
		Platform.case { \windows } {

			(NN.filenameSymbol.asString.dirname +/+ "../ignore/*.dll").pathMatch.do { |dll|
				var file = Platform.resourceDir +/+ dll.basename;
				try {
					if(File.delete(file)) {
						"*** % deleted".format(file).postln;
					} {

						"couldn't delete %".format(file).warn;
					}
				} { |err|
					err.errorString.postln;
				}
			}
		} {
			warn("You don't need to delete DLLs unless you're on Windows");
		}
	}

	*deQuarantine {
		Platform.case { \osx } {	
			var path = PathName(NN.filenameSymbol.asString.dirname).parentPath;
			["*.scx", "ignore/*.dylib"].do { |ext| 
				(path +/+ ext).pathMatch.do { |path|
					var cmd = "xattr -d com.apple.quarantine" + shellQuote(path);
					cmd.postln.unixCmd
				}
			}
		} {
			warn("You don't need to delete DLLs unless you're on macOS");
		}
	}
}

