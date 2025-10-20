package com.encodeforge.controller.components;

/**
 * Base interface for all sub-controllers in the modular architecture.
 * Provides lifecycle methods for initialization and cleanup.
 */
public interface ISubController {
    
    /**
     * Initialize the controller and set up UI bindings.
     * Called after construction and dependency injection.
     */
    void initialize();
    
    /**
     * Clean up resources and prepare for shutdown.
     * Called before application exit.
     */
    void shutdown();
}

