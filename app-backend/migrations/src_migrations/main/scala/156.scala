import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M156 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(156)(List(
    sqlu"""
    CREATE TABLE project_layers (
      id           UUID PRIMARY KEY NOT NULL,
      created_at   TIMESTAMP NOT NULL,
      modified_at  TIMESTAMP NOT NULL,
      name         TEXT NOT NULL,
      project      UUID REFERENCES projects(id) NOT NULL,
      smart_layer  BOOLEAN NOT NULL DEFAULT FALSE,
      range_start  TIMESTAMP,
      range_end    TIMESTAMP
    );

    SELECT AddGeometryColumn('project_layers', 'geometry', 3857, 'GEOMETRY', 2);
    """
  ))
}
