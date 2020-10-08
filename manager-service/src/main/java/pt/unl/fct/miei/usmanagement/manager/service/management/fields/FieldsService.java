/*
 * MIT License
 *
 * Copyright (c) 2020 manager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package pt.unl.fct.miei.usmanagement.manager.service.management.fields;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pt.unl.fct.miei.usmanagement.manager.database.fields.FieldEntity;
import pt.unl.fct.miei.usmanagement.manager.database.fields.FieldRepository;
import pt.unl.fct.miei.usmanagement.manager.service.exceptions.EntityNotFoundException;
import pt.unl.fct.miei.usmanagement.manager.service.util.ObjectUtils;

import java.util.List;

@Slf4j
@Service
public class FieldsService {

	private final FieldRepository fields;

	public FieldsService(FieldRepository fields) {
		this.fields = fields;
	}

	public List<FieldEntity> getFields() {
		return fields.findAll();
	}

	public FieldEntity getField(Long id) {
		return fields.findById(id).orElseThrow(() ->
			new EntityNotFoundException(FieldEntity.class, "id", id.toString()));
	}

	public FieldEntity getField(String name) {
		return fields.findByNameIgnoreCase(name).orElseThrow(() ->
			new EntityNotFoundException(FieldEntity.class, "name", name));
	}

	public FieldEntity addField(FieldEntity field) {
		assertFieldDoesntExist(field);
		log.info("Saving field {}", ToStringBuilder.reflectionToString(field));
		return fields.save(field);
	}

	public FieldEntity updateField(String fieldName, FieldEntity newField) {
		FieldEntity field = getField(fieldName);
		log.info("Updating field {} with {}",
			ToStringBuilder.reflectionToString(field), ToStringBuilder.reflectionToString(newField));
		log.info("Field before copying properties: {}",
			ToStringBuilder.reflectionToString(field));
		ObjectUtils.copyValidProperties(newField, field);
		log.info("Field after copying properties: {}",
			ToStringBuilder.reflectionToString(field));
		field = fields.save(field);
		return field;
	}

	public void deleteField(String fieldName) {
		FieldEntity field = getField(fieldName);
		fields.delete(field);
	}

	private void assertFieldDoesntExist(FieldEntity field) {
		String fieldName = field.getName();
		if (fields.hasField(fieldName)) {
			throw new DataIntegrityViolationException("Field '" + fieldName + "' already exists");
		}
	}

}