
package io.objectbox.tree;

import io.objectbox.EntityInfo;
import io.objectbox.annotation.apihint.Internal;
import io.objectbox.internal.CursorFactory;
import io.objectbox.internal.IdGetter;
import io.objectbox.internal.ToOneGetter;
import io.objectbox.relation.RelationInfo;
import io.objectbox.relation.ToOne;
import io.objectbox.tree.DataLeafCursor.Factory;

// THIS CODE IS GENERATED BY ObjectBox, DO NOT EDIT.

/**
 * Properties for entity "DataLeaf". Can be used for QueryBuilder and for referencing DB names.
 */
public final class DataLeaf_ implements EntityInfo<DataLeaf> {

    // Leading underscores for static constants to avoid naming conflicts with property names

    public static final String __ENTITY_NAME = "DataLeaf";

    public static final int __ENTITY_ID = 46;

    public static final Class<DataLeaf> __ENTITY_CLASS = DataLeaf.class;

    public static final String __DB_NAME = "DataLeaf";

    public static final CursorFactory<DataLeaf> __CURSOR_FACTORY = new Factory();

    @Internal
    static final DataLeafIdGetter __ID_GETTER = new DataLeafIdGetter();

    public final static DataLeaf_ __INSTANCE = new DataLeaf_();

    public final static io.objectbox.Property<DataLeaf> id =
        new io.objectbox.Property<>(__INSTANCE, 0, 1, long.class, "id", true, "id");

    public final static io.objectbox.Property<DataLeaf> valueInt =
        new io.objectbox.Property<>(__INSTANCE, 1, 2, long.class, "valueInt");

    public final static io.objectbox.Property<DataLeaf> valueDouble =
        new io.objectbox.Property<>(__INSTANCE, 2, 3, double.class, "valueDouble");

    public final static io.objectbox.Property<DataLeaf> valueString =
        new io.objectbox.Property<>(__INSTANCE, 3, 4, String.class, "valueString");

    public final static io.objectbox.Property<DataLeaf> valueStrings =
        new io.objectbox.Property<>(__INSTANCE, 4, 5, String[].class, "valueStrings");

    public final static io.objectbox.Property<DataLeaf> dataBranchId =
        new io.objectbox.Property<>(__INSTANCE, 5, 6, long.class, "dataBranchId", true);

    public final static io.objectbox.Property<DataLeaf> metaLeafId =
        new io.objectbox.Property<>(__INSTANCE, 6, 7, long.class, "metaLeafId", true);

    @SuppressWarnings("unchecked")
    public final static io.objectbox.Property<DataLeaf>[] __ALL_PROPERTIES = new io.objectbox.Property[]{
        id,
        valueInt,
        valueDouble,
        valueString,
        valueStrings,
        dataBranchId,
        metaLeafId
    };

    public final static io.objectbox.Property<DataLeaf> __ID_PROPERTY = id;

    @Override
    public String getEntityName() {
        return __ENTITY_NAME;
    }

    @Override
    public int getEntityId() {
        return __ENTITY_ID;
    }

    @Override
    public Class<DataLeaf> getEntityClass() {
        return __ENTITY_CLASS;
    }

    @Override
    public String getDbName() {
        return __DB_NAME;
    }

    @Override
    public io.objectbox.Property<DataLeaf>[] getAllProperties() {
        return __ALL_PROPERTIES;
    }

    @Override
    public io.objectbox.Property<DataLeaf> getIdProperty() {
        return __ID_PROPERTY;
    }

    @Override
    public IdGetter<DataLeaf> getIdGetter() {
        return __ID_GETTER;
    }

    @Override
    public CursorFactory<DataLeaf> getCursorFactory() {
        return __CURSOR_FACTORY;
    }

    @Internal
    static final class DataLeafIdGetter implements IdGetter<DataLeaf> {
        @Override
        public long getId(DataLeaf object) {
            return object.id;
        }
    }

    /** To-one relation "dataBranch" to target entity "DataBranch". */
    public static final RelationInfo<DataLeaf, DataBranch> dataBranch =
            new RelationInfo<>(DataLeaf_.__INSTANCE, io.objectbox.tree.DataBranch_.__INSTANCE, dataBranchId, new ToOneGetter<DataLeaf>() {
                @Override
                public ToOne<DataBranch> getToOne(DataLeaf entity) {
                    return entity.dataBranch;
                }
            });

    /** To-one relation "metaLeaf" to target entity "MetaLeaf". */
    public static final RelationInfo<DataLeaf, MetaLeaf> metaLeaf =
            new RelationInfo<>(DataLeaf_.__INSTANCE, io.objectbox.tree.MetaLeaf_.__INSTANCE, metaLeafId, new ToOneGetter<DataLeaf>() {
                @Override
                public ToOne<MetaLeaf> getToOne(DataLeaf entity) {
                    return entity.metaLeaf;
                }
            });

}
