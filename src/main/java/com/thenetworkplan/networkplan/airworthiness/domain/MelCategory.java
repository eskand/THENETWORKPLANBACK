package com.thenetworkplan.networkplan.airworthiness.domain;

/** MMEL rectification interval category. */
public enum MelCategory {

    /** No standard interval, rectify as stated in the MMEL. */
    A,

    /** Three calendar days. */
    B,

    /** Ten calendar days. */
    C,

    /** One hundred and twenty calendar days. */
    D
}
