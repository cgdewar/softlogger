package ca.brood.softlogger.lookuptable;

public class TestGenerator implements GenerationFunction {

	@Override
	public double process(int index) {
		return 2*index;
	}

}