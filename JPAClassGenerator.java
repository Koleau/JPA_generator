package com.ipsosenso.ipsobox;

import com.google.common.base.CaseFormat;
import com.squareup.javapoet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.lang.model.element.Modifier;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;


/**
 * Created by Pflieger on 24/05/2016.
 */
public class JPAClassGenerator {


    private static final Logger LOGGER = LoggerFactory.getLogger(JPAClassGenerator.class);

    private static String url = "jdbc:mysql://localhost:3306/hds";
    private static String user = "root";
    private static String password = "root";

    private static String pathToJava= "C:\\Users\\Pflieger\\travail\\ipsosenso\\projets\\ocirp\\hds\\src\\main\\java\\";
    private static String beanPackage = "com.ipsosenso.ipsobox.beans";
//    private static String pathToRepo = "C:\\Users\\Pflieger\\travail\\ipsosenso\\projets\\ocirp\\hds\\src\\main\\java\\";
    private static String repoPackage = "com.ipsosenso.ipsobox.repos";

    public static void main(String[] args) throws SQLException, IOException {
//        Connection jdbcConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test_ocirp", "popo", "azerty");
        Connection jdbcConnection = DriverManager.getConnection(url, user, password);
        DatabaseMetaData m = jdbcConnection.getMetaData();

        // Table name
        ResultSet rs = m.getTables(null, null, "%", null);
        while (rs.next()) {
            String tableName = rs.getString(3);
            LOGGER.debug("table : " + tableName);

            String CCTableName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, tableName);

            // Constructor
            MethodSpec constructor = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .build();

            // Object
            TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(CCTableName)
                    .addAnnotation(Entity.class)
                    .addAnnotation(AnnotationSpec.builder(Table.class)
                            .addMember("name", "$S", tableName)
                            .build())
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(constructor);

            // Columns
            ResultSet rs2 = m.getColumns(null, null, tableName, "%");
            System.out.println("__________________");
            System.out.println(CCTableName);

            while (rs2.next()) {

                String columnName = rs2.getString(4);
                String LCCcolumnName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, columnName);
                String CCcolumnName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, columnName);

                String columnType = rs2.getString(6);
                Class<?> fieldType = StringToClass(columnType);
                boolean canBeNull = rs2.getBoolean(11);
                String auto_inc = rs2.getString(23);


                if (fieldType != null) {
                    // TODO: TEST pour champs avec jointures
                    // field's creation and @Column(name = "[colomn_name]")
                    FieldSpec.Builder fieldSpec = FieldSpec.builder(fieldType, LCCcolumnName)
                            .addModifiers(Modifier.PRIVATE)
                            .addAnnotation(AnnotationSpec.builder(Column.class)
                                    .addMember("name", "$S", columnName)
                                    .build());
                    if (columnName.matches("^(id_).*$")) {
                        fieldSpec.addJavadoc("TODO: Object is bind to another, it must be done manually");
                    } else {
                        if (!canBeNull) {
                            //@NotNull
                            fieldSpec.addAnnotation(NotNull.class);
                        }
                        if (columnName.equals("id")) {
                            //@Id
                            fieldSpec.addAnnotation(Id.class);
                            if (auto_inc.equals("YES")) {
                                // @GeneratedValue(strategy = GenerationType.AUTO)
                                fieldSpec.addAnnotation(AnnotationSpec.builder(GeneratedValue.class)
                                        .addMember("strategy", "$N", "GenerationType.AUTO")
                                        .build());
                            }
                        }
                    }

                    typeSpecBuilder.addField(fieldSpec.build());
                    typeSpecBuilder.addMethod(generateGetter(CCcolumnName, LCCcolumnName, fieldType));
                    typeSpecBuilder.addMethod(generateSetter(CCcolumnName, LCCcolumnName, fieldType));
                }

            }
            JavaFile javaFile = JavaFile.builder(beanPackage, typeSpecBuilder.build()).build();
            javaFile.writeTo(new File(pathToJava));

            // Repository
            // Repository type name
            TypeName repository = ParameterizedTypeName.get(JpaRepository.class, Object.class);

            //
            ClassName namedBoards = ClassName.get(beanPackage, CCTableName);

            // Repository
            TypeSpec repositorySpec = TypeSpec.interfaceBuilder(CCTableName+"Repository")
                    .addAnnotation(Repository.class)
                    .addSuperinterface(ParameterizedTypeName.get(
                            ClassName.get(JpaRepository.class),
                            namedBoards,
                            ClassName.get(Integer.class)
                            ))
                    .addModifiers(Modifier.PUBLIC)
                    .build();
            javaFile = JavaFile.builder(repoPackage, repositorySpec).build();
            javaFile.writeTo(new File(pathToJava));
        }

    }

    /**
     * 3
     * @param typeString
     * @return
     */
    private static Class<?> StringToClass(String typeString) {

        switch (typeString) {

            case "VARCHAR":
                return String.class;

            case "BIGINT":
                return long.class;

            case "DATETIME":
                return java.util.Date.class;

            case "LONGTEXT":
                return String.class;

            case "BIT":
                return boolean.class;

            case "INT":
                return int.class;

            default:
                System.out.println("type non trouvé : " + typeString);
                return null;
        }
    }

    /**
     * Function to generate Method spec for a getter
     * @param name
     * @param lname
     * @param type
     * @return
     */
    private static MethodSpec generateGetter(String name, String lname, Class<?> type) {
        return MethodSpec.methodBuilder("get" + name)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return this.$N", lname)
                .returns(type)
                .build();

    }

    /**
     * Function to generate Method spec for a setter
     * @param name
     * @param lname
     * @param type
     * @return
     */
    private static MethodSpec generateSetter(String name, String lname, Class<?> type) {
        return MethodSpec.methodBuilder("set" + name)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type, lname)
                .addStatement("this.$N = $N", lname, lname)
                .build();
    }

}
