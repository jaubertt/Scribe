package scribe.client.screen;

public final class WheelSteps {
	private double rest;

	public int take(double amount) {
		this.rest -= amount;
		int steps = (int) this.rest;
		this.rest -= steps;
		return steps;
	}
}
