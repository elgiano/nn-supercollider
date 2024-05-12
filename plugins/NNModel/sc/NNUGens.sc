NNUGen : MultiOutUGen {

	// enum UGenInputs { modelIdx=0, methodIdx, bufSize, warmup, debug, nBatches, inputs };
	// todo: clump batches
	*ar { |modelIdx, methodIdx, bufferSize, numOutputs, warmup, debug, nBatches, inputs|
		^this.new1('audio', modelIdx, methodIdx, bufferSize, warmup, debug, nBatches, *inputs)
			.initOutputs(numOutputs * nBatches, 'audio');
	}

	checkInputs {
		// modelIdx, methodIdx and bufferSize are not modulatable
		['modelIdx', 'methodIdx', 'bufferSize'].do { |name, n|
		if (inputs[n].rate != \scalar) {
				^": '%' is not modulatable. Got: %.".format(name, inputs[n]);	
			}
		}
		^this.checkValidInputs;
	}
}

+NNModelMethod {
	ar { |inputs, bufferSize=(-1), warmup=0, debug=0, attributes(#[])|
		var attrParams, nBatches, outputs;
		inputs = inputs.asArray;


		case { inputs.rank == 1 && {inputs.size == this.numInputs} } {
			nBatches = 1;
		}
		{ inputs.rank == 1 && {this.numInputs == 1} } {
			nBatches = inputs.size;
		}
		{ inputs.rank == 2 && {inputs.every{ |b| b.size == this.numInputs }} } {
			nBatches = inputs.size;
			// ugen wants interlaced latents:
			// e.g. inputs (a,b) with latents (0..2): a0, b0, a1, b1, a2, b2 
			inputs = inputs.lace;
		} {
			Error("NNModel: method % has % inputs, but was given %."
				.format(this.name, this.numInputs, inputs.shape)).throw
		};

		attrParams = Array(attributes.size);
		attributes.pairsDo { |attrName, attrValue|
			attrParams.add(model.attrIdx(attrName));
			attrParams.add(attrValue ?? 0);
		};

		outputs = NNUGen.ar(model.idx, idx, bufferSize, this.numOutputs, warmup, debug, nBatches, inputs ++ attrParams);
		// ugen outputs interlaced batched outputs: unlace
		// e.g. a0, b0, a1, b1 ... -> unlace to [[a0,a1], [b0,b1]]
		if (nBatches > 1) {
			outputs = outputs.unlace(nBatches);
			if (this.numOutputs == 1) {
				// flat [[a0], [b0]] to [a0, b0]
				outputs = outputs.flatten;
			}
		};
		^outputs
	}
}
