package com.tibyaan.cli.describe;

/**
 * The one question {@code tibyan describe} asks a model (Volume 5 section 4.15.5.2): given the prompt built from
 * a fingerprinted slice, return the drafted description text. The interface exists so the command is tested
 * against a fake and so the only network-capable code in the tool has one seam.
 */
public interface DescriptionModel {

    /** The model id recorded on every entry this model describes. */
    String id();

    /** Returns the model's text for the prompt, or throws {@link ModelException} with the reason. */
    String describe(String prompt) throws ModelException;

    /** A refusal from the model side: transport, status, or an unreadable response. Never the tool's fault. */
    final class ModelException extends Exception {
        public ModelException(String message) {
            super(message);
        }

        public ModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
