// NNModel can be constructed only by:
// - *load: by loading a model on the server
// - *read: by reading an info file (for NRT)

NNModel {

	var <server, <path, <idx, <info, <methods;

	*new { ^nil }

	minBufferSize { ^if (info.isNil) { nil } { info.minBufferSize } }
	attributes { ^if(info.isNil) { nil } { info.attributes } }
	attrIdx { |attrName|
		var attrs = this.attributes ?? { ^nil };
		^attrs.indexOf(attrName);
	}

	key { ^NN.keyForModel(this) }

	method { |name|
		var method;
		this.methods ?? { Error("NNModel % has no methods.".format(this.key)).throw };
		^this.methods.detect { |m| m.name == name };
	}

	*load { |path, id(-1), server(Server.default), action|
		var loadMsg, infoFile, model;
		path = path.standardizePath;
		if (server.serverRunning.not) {
			Error("server not running").throw
		};
		if (File.exists(path).not) {
			Error("model file '%' not found".format(path)).throw
		};

		infoFile = infoFile ?? {PathName.tmp +/+ "nn-sc-" ++ UniqueID.next ++ ".yaml"};
		loadMsg = NN.loadMsg(id, path, infoFile);

		model = super.newCopyArgs(server);

		NN.prIfCmd(server, loadMsg) {
			// server wrote an infoFile: read it
			protect { 
				model.initFromFile(infoFile);
				action.value(model)
			} {
				File.delete(infoFile);
			}
		};

		^model;
	}

	reload { |action|
		var key = this.key;
		// delete key first, otherwise NN.load is no-op
		NN.prDeleteModel(key);
		NN.load(key, path, idx, server, action);
	}

	*fromInfo { |info, overrideId, server(Server.default)|
		^super.newCopyArgs(server).initFromInfo(info, overrideId);
	}

	initFromFile { |infoFile|
		var info = NNModelInfo.fromFile(infoFile);
		this.initFromInfo(info);
		NN.prCacheInfo(info);
	}

	initFromInfo { |infoObj, overrideId|
		info = infoObj;
		path = info.path;
		idx = overrideId ? info.idx;
		methods = info.methods.collect { |m| m.copyForModel(this) };
	}

	loadMsg { |newPath, infoFile|
		^NN.loadMsg(idx, newPath ? path, infoFile)
	}

	dumpInfoMsg { |outFile| ^NN.dumpInfoMsg(this.idx, outFile) }
	dumpInfo { |outFile|
		var msg = this.dumpInfoMsg(outFile);
		this.prErrIfNoServer("dumpInfo");
		if (server.serverRunning.not) { Error("server not running").throw };
		server.sendMsg(*msg);
	}

	describe {
		"\n*** NNModel(%)".format(this.key).postln;
		this.info.describe;
	}

	printOn { |stream|
		stream << "NNModel(" <<* [this.key, this.minBufferSize] << ")";
	}

	prErrIfNoServer { |funcName|
	if (server.isNil) {
		Error("NNModel(%) is not bound to a server, can't %. Is it a NRT model?"
			.format(funcName, this.key)).throw
		};
	}
}

NNModelInfo {
	var <idx, <path, <minBufferSize, <methods, <attributes;
	*new {}

	*fromFile { |infoFile|
		if (File.exists(infoFile).not) {
			Error("NNModelInfo: file '%' does not exist".format(infoFile)).throw;
		} {
			var yaml = File.readAllString(infoFile).parseYAML[0];
			^super.new.initFromDict(yaml)
		}
	}
	*fromDict { |infoDict|
		^super.new.initFromDict(infoDict);
	}
	initFromDict { |yaml|
		idx = yaml["idx"].asInteger;
		path = yaml["modelPath"];
		minBufferSize = yaml["minBufferSize"].asInteger;
		methods = yaml["methods"].collect { |m, n|
			var name = m["name"].asSymbol;
			var inDim = m["inDim"].asInteger;
			var outDim = m["outDim"].asInteger;
			NNModelMethod(nil, name, n, inDim, outDim);
		};
		attributes = yaml["attributes"].collect(_.asSymbol) ?? { [] }
	}

	describe {
		"path: %".format(this.path).postln;
		"minBufferSize: %".format(this.minBufferSize).postln;
		this.methods.do { |m|
			"- method %: % ins, % outs".format(m.name, m.numInputs, m.numOutputs).postln;
		};
		"".postln;
	}
}

NNModelMethod {
	var <model, <name, <idx, <numInputs, <numOutputs;

	*new { |...args| ^super.newCopyArgs(*args) }

	copyForModel { |model|
		^this.class.newCopyArgs(model, name, idx, numInputs, numOutputs)
	}

	printOn { |stream|
		stream << "%(%: % in, % out)".format(this.class.name, name, numInputs, numOutputs);
	}
}
