package com.galgothstudio.backend.aiorchestrator.texture;

/** El `boneId` pedido para "Regenerar textura" (HU-37) no existe en el modelo actual del mob -- 400, nunca se crea una fila `ai_jobs`. */
public class TextureTargetBoneNotFoundException extends RuntimeException {

	public TextureTargetBoneNotFoundException(String boneId) {
		super("El modelo no tiene ningún bone con id '" + boneId + "' -- no hay nada que regenerar.");
	}

}
