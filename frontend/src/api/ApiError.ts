/** Error HTTP normalizado -- cualquier cliente de `src/api/`/`src/projects/`/`src/editor/` lo usa para representar una respuesta no-2xx del backend de forma consistente. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}
