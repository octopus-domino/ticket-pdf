package ru.ticket.email.render;

public enum RenderMode {

    /** Browsers render it inside the email-preview iframe. Vector. */
    PREVIEW,

    /** Email clients: SVG unsupported → rasterize to PNG, embed via CID. */
    EMAIL
}