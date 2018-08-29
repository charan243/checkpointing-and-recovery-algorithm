package com.aos;

import java.util.Random;

public class Exprv {
	private double mean;
	private Random uni_rv;
	public Exprv(double mean) {
		this.mean = mean;
		uni_rv = new Random();
	}
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Exprv exp = new Exprv(20);
		for(int i=0;i<10;i++) {
			System.out.println(exp.exp_rv()+",");
		}
	}

	Double exp_rv() {
		Double exp = (-1) * mean * Math.log(uni_rv.nextDouble());
		return exp;
	}
}
