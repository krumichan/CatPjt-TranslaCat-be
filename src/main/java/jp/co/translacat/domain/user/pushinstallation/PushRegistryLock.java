package jp.co.translacat.domain.user.pushinstallation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "push_registry_lock")
public class PushRegistryLock {
    @Id
    private Integer id;

    protected PushRegistryLock() {}
    public PushRegistryLock(int id) { this.id = id; }
}
